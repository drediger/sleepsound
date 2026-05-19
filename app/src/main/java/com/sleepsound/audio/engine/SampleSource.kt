package com.sleepsound.audio.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.sleepsound.audio.BUFFER_FRAMES
import com.sleepsound.audio.SAMPLE_RATE
import com.sleepsound.audio.SHORT_MAX
import com.sleepsound.audio.SHORT_MIN
import com.sleepsound.audio.procedural.NoiseSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Streams an audio asset through a [MediaCodec] decoder, producing 48 kHz
 * stereo PCM chunks into a jitter queue that [fillBuffer] consumes from.
 * Memory cost is bounded by [JITTER_TARGET_FRAMES] regardless of the asset's
 * length, so a 10-minute loop costs the same RAM as a 30-second one. Loops
 * seamlessly by seeking the extractor back to 0 and flushing the codec on
 * EOS — the decoder coroutine keeps the queue ahead of the audio thread so
 * the loop point is inaudible.
 *
 * Source assets should be 48 kHz preferred. Other rates linearly resample.
 * Mono is upmixed to stereo. Any format MediaCodec handles works (OGG, OPUS,
 * MP3, AAC, FLAC, WAV); OGG Vorbis is the recommended target.
 */
class SampleSource(
    private val context: Context,
    private val assetPath: String,
    private val scope: CoroutineScope,
) : NoiseSource {

    private val chunks = ConcurrentLinkedQueue<ShortArray>()
    private val bufferedFrames = AtomicInteger(0)
    private var decodeJob: Job? = null

    // Read-side state — only touched from the audio thread that calls fillBuffer.
    private var currentChunk: ShortArray? = null
    private var currentChunkPos: Int = 0

    fun preload() {
        if (decodeJob?.isActive == true) return
        decodeJob = scope.launch(Dispatchers.IO) { decodeLoop() }
    }

    fun release() {
        decodeJob?.cancel()
        decodeJob = null
        chunks.clear()
        bufferedFrames.set(0)
    }

    private suspend fun decodeLoop() {
        val afd = try {
            context.assets.openFd(assetPath)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot open asset $assetPath", e)
            return
        }
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            val (trackIndex, format) = findAudioTrack(extractor)
                ?: run { Log.w(TAG, "No audio track in $assetPath"); return }
            extractor.selectTrack(trackIndex)

            val srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val srcChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = format.getString(MediaFormat.KEY_MIME)!!

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var sentEos = false

            while (currentCoroutineContext().isActive) {
                // Backpressure — pause decode when the jitter queue is full.
                if (bufferedFrames.get() >= JITTER_TARGET_FRAMES) {
                    delay(20)
                    continue
                }

                if (!sentEos) {
                    val inIdx = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inIdx >= 0) {
                        val ib = codec.getInputBuffer(inIdx)
                        val read = if (ib != null) extractor.readSampleData(ib, 0) else -1
                        if (read < 0) {
                            codec.queueInputBuffer(
                                inIdx, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            sentEos = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, read, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
                if (outIdx >= 0) {
                    if (info.size > 0) {
                        val ob = codec.getOutputBuffer(outIdx)
                        if (ob != null) {
                            ob.position(info.offset)
                            ob.limit(info.offset + info.size)
                            val raw = ShortArray(ob.asShortBuffer().remaining())
                            ob.asShortBuffer().get(raw)
                            val stereo48k = transformChunk(raw, srcRate, srcChannels)
                            if (stereo48k.isNotEmpty()) {
                                chunks.offer(stereo48k)
                                bufferedFrames.addAndGet(stereo48k.size / 2)
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)

                    val isEos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    if (isEos) {
                        // Loop point. Reset the extractor + codec and keep going.
                        codec.flush()
                        codec.start()
                        extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                        sentEos = false
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Decode loop failed for $assetPath", e)
        } finally {
            codec?.runCatching { stop() }
            codec?.runCatching { release() }
            extractor.release()
            afd.close()
        }
    }

    /**
     * Resample + upmix per-chunk. Linear interpolation across chunk
     * boundaries is left to MediaCodec's PCM continuity (the codec emits
     * contiguous samples; we never split a frame).
     */
    private fun transformChunk(raw: ShortArray, srcRate: Int, srcChannels: Int): ShortArray {
        val stereo = if (srcChannels == 1) monoToStereo(raw) else raw
        return if (srcRate != SAMPLE_RATE) resample(stereo, srcRate) else stereo
    }

    private fun monoToStereo(mono: ShortArray): ShortArray {
        val stereo = ShortArray(mono.size * 2)
        for (i in mono.indices) {
            stereo[i * 2] = mono[i]
            stereo[i * 2 + 1] = mono[i]
        }
        return stereo
    }

    private fun resample(input: ShortArray, srcRate: Int): ShortArray {
        val srcFrames = input.size / 2
        if (srcFrames == 0) return ShortArray(0)
        val dstFrames = (srcFrames.toLong() * SAMPLE_RATE / srcRate).toInt()
        val result = ShortArray(dstFrames * 2)
        val ratio = srcRate.toDouble() / SAMPLE_RATE
        for (i in 0 until dstFrames) {
            val srcPos = i * ratio
            val srcIdx = srcPos.toInt().coerceAtMost(srcFrames - 1)
            val nextSrc = (srcIdx + 1).coerceAtMost(srcFrames - 1)
            val frac = (srcPos - srcIdx).toFloat()
            val invFrac = 1f - frac
            val l = input[srcIdx * 2] * invFrac + input[nextSrc * 2] * frac
            val r = input[srcIdx * 2 + 1] * invFrac + input[nextSrc * 2 + 1] * frac
            result[i * 2] = l.toInt().coerceIn(SHORT_MIN, SHORT_MAX).toShort()
            result[i * 2 + 1] = r.toInt().coerceIn(SHORT_MIN, SHORT_MAX).toShort()
        }
        return result
    }

    private fun findAudioTrack(extractor: MediaExtractor): Pair<Int, MediaFormat>? {
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i to f
        }
        return null
    }

    override fun fillBuffer(out: ShortArray, frames: Int) {
        val samplesNeeded = frames * 2
        var outPos = 0

        while (outPos < samplesNeeded) {
            var chunk = currentChunk
            if (chunk == null || currentChunkPos >= chunk.size) {
                chunk = chunks.poll()
                if (chunk == null) break  // underflow — decoder behind
                currentChunk = chunk
                currentChunkPos = 0
            }

            val available = chunk.size - currentChunkPos
            val toCopy = minOf(samplesNeeded - outPos, available)
            System.arraycopy(chunk, currentChunkPos, out, outPos, toCopy)
            currentChunkPos += toCopy
            outPos += toCopy
            bufferedFrames.addAndGet(-(toCopy / 2))
        }

        // Underflow — pad with silence. Audio will glitch briefly; the decode
        // loop will catch up on the next render tick.
        while (outPos < samplesNeeded) {
            out[outPos++] = 0
        }
    }

    companion object {
        private const val TAG = "SampleSource"
        private const val JITTER_TARGET_FRAMES = SAMPLE_RATE  // ~1 s of stereo PCM
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        // BUFFER_FRAMES referenced to keep the import in case fillBuffer
        // is ever sized differently from the engine's expected frame count.
        @Suppress("unused")
        private const val ENGINE_BUFFER_FRAMES = BUFFER_FRAMES
    }
}
