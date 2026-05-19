package com.sleepsound.audio.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.sleepsound.audio.SAMPLE_RATE
import com.sleepsound.audio.SHORT_MAX
import com.sleepsound.audio.SHORT_MIN

/**
 * Decodes an audio asset file to 16-bit stereo PCM at 48 kHz, ready to be
 * looped through the existing mixer pipeline. Supports any format MediaCodec
 * can decode (OGG Vorbis, OPUS, MP3, AAC, FLAC, WAV).
 *
 * Mono is upmixed to stereo. Non-48 kHz is linearly resampled.
 */
object AudioDecoder {

    fun decodeToPcm(context: Context, assetPath: String): ShortArray {
        val afd = context.assets.openFd(assetPath)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            val (trackIndex, format) = findAudioTrack(extractor)
                ?: error("No audio track in $assetPath")
            extractor.selectTrack(trackIndex)

            val srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val srcChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val raw = readDecodedShorts(extractor, codec)
            val stereo = if (srcChannels == 1) monoToStereo(raw) else raw
            return if (srcRate != SAMPLE_RATE) resample(stereo, srcRate, SAMPLE_RATE) else stereo
        } finally {
            codec?.runCatching { stop() }
            codec?.runCatching { release() }
            extractor.release()
            afd.close()
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Pair<Int, MediaFormat>? {
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i to f
        }
        return null
    }

    private fun readDecodedShorts(extractor: MediaExtractor, codec: MediaCodec): ShortArray {
        // Collect primitive ShortArray chunks instead of boxing into List<Short>;
        // a 30s 48k stereo file would otherwise allocate ~90 MB of boxed shorts.
        val chunks = ArrayList<ShortArray>()
        val info = MediaCodec.BufferInfo()
        var endOfInput = false
        var endOfOutput = false

        while (!endOfOutput) {
            if (!endOfInput) {
                val inputIndex = codec.dequeueInputBuffer(10_000L)
                if (inputIndex >= 0) {
                    val buffer = codec.getInputBuffer(inputIndex)!!
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        endOfInput = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outIndex = codec.dequeueOutputBuffer(info, 10_000L)
            if (outIndex >= 0) {
                if (info.size > 0) {
                    val buffer = codec.getOutputBuffer(outIndex)!!
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val shorts = buffer.asShortBuffer()
                    val chunk = ShortArray(shorts.remaining())
                    shorts.get(chunk)
                    chunks.add(chunk)
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) endOfOutput = true
            }
        }

        val total = chunks.sumOf { it.size }
        val out = ShortArray(total)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(out, offset)
            offset += chunk.size
        }
        return out
    }

    private fun monoToStereo(mono: ShortArray): ShortArray {
        val stereo = ShortArray(mono.size * 2)
        for (i in mono.indices) {
            stereo[i * 2] = mono[i]
            stereo[i * 2 + 1] = mono[i]
        }
        return stereo
    }

    private fun resample(input: ShortArray, srcRate: Int, dstRate: Int): ShortArray {
        if (srcRate == dstRate) return input
        val srcFrames = input.size / 2
        val dstFrames = (srcFrames.toLong() * dstRate / srcRate).toInt()
        val result = ShortArray(dstFrames * 2)
        val ratio = srcRate.toDouble() / dstRate.toDouble()
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
}
