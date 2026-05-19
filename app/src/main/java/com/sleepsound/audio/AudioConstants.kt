package com.sleepsound.audio

import kotlin.math.PI

const val SAMPLE_RATE = 48_000
const val BUFFER_FRAMES = 4096
const val SHORT_MIN = -32_768
const val SHORT_MAX = 32_767

val TWO_PI_F: Float = (2.0 * PI).toFloat()
