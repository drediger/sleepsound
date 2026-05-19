package com.sleepsound.audio

import android.content.Context
import com.sleepsound.audio.engine.SampleSource
import com.sleepsound.audio.procedural.BrownNoise
import com.sleepsound.audio.procedural.Dryer
import com.sleepsound.audio.procedural.Fan
import com.sleepsound.audio.procedural.Fireplace
import com.sleepsound.audio.procedural.NoiseSource
import com.sleepsound.audio.procedural.Ocean
import com.sleepsound.audio.procedural.PinkNoise
import com.sleepsound.audio.procedural.Rain
import com.sleepsound.audio.procedural.Thunderstorm
import com.sleepsound.audio.procedural.VioletNoise
import com.sleepsound.audio.procedural.WhiteNoise
import kotlinx.coroutines.CoroutineScope

/**
 * Sample-first lookup: when an asset exists at `sounds/<sound_id>.<ext>`
 * (ogg / opus / mp3 / flac / wav, case-insensitive), it's loaded via
 * [SampleSource]; otherwise the procedural generator runs as a fallback.
 *
 * Drop seamless ~10–30s loops into `app/src/main/assets/sounds/` to override
 * any sound with a real recording.
 */
object SoundCatalog {

    private val sampleExtensions = listOf("ogg", "opus", "mp3", "flac", "wav")

    fun create(context: Context, scope: CoroutineScope, id: SoundId): NoiseSource {
        for (ext in sampleExtensions) {
            val path = "sounds/${id.name.lowercase()}.$ext"
            if (assetExists(context, path)) {
                return SampleSource(context, path, scope).also { it.preload() }
            }
        }
        return createProcedural(id)
    }

    fun createAll(context: Context, scope: CoroutineScope): Map<SoundId, NoiseSource> =
        SoundId.entries.associateWith { create(context, scope, it) }

    private fun assetExists(context: Context, path: String): Boolean = runCatching {
        context.assets.open(path).close()
        true
    }.getOrDefault(false)

    private fun createProcedural(id: SoundId): NoiseSource = when (id) {
        SoundId.BROWN_NOISE -> BrownNoise()
        SoundId.PINK_NOISE -> PinkNoise()
        SoundId.WHITE_NOISE -> WhiteNoise()
        SoundId.VIOLET_NOISE -> VioletNoise()
        SoundId.RAIN -> Rain()
        SoundId.THUNDERSTORM -> Thunderstorm()
        SoundId.DRYER -> Dryer()
        SoundId.OCEAN -> Ocean()
        SoundId.FAN -> Fan()
        SoundId.FIREPLACE -> Fireplace()
    }
}
