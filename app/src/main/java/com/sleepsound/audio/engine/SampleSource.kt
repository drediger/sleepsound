package com.sleepsound.audio.engine

import android.content.Context
import android.util.Log
import com.sleepsound.audio.procedural.NoiseSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Loops a decoded sample asset as a [NoiseSource]. Decode runs on
 * [Dispatchers.IO] when [preload] is first called; the source emits silence
 * until decoding completes (typically < 1 second for short loops).
 *
 * Source assets should be 48 kHz preferred (44.1k auto-resamples linearly),
 * stereo or mono (mono is upmixed). Aim for a seamless 10–30 second loop
 * cut at a zero-crossing for click-free repetition.
 */
class SampleSource(
    private val context: Context,
    private val assetPath: String,
    private val scope: CoroutineScope,
) : NoiseSource {

    @Volatile private var pcm: ShortArray? = null
    @Volatile private var loadJob: Job? = null
    private var pos = 0

    fun preload() {
        if (pcm != null || loadJob?.isActive == true) return
        loadJob = scope.launch(Dispatchers.IO) {
            try {
                pcm = AudioDecoder.decodeToPcm(context, assetPath)
            } catch (e: Exception) {
                Log.w("SampleSource", "Failed to decode $assetPath", e)
            }
        }
    }

    override fun fillBuffer(out: ShortArray, frames: Int) {
        val p = pcm
        val samples = frames * 2
        if (p == null || p.isEmpty()) {
            preload()
            for (i in 0 until samples) out[i] = 0
            return
        }
        var p_pos = pos
        for (i in 0 until samples) {
            out[i] = p[p_pos]
            p_pos++
            if (p_pos >= p.size) p_pos = 0
        }
        pos = p_pos
    }
}
