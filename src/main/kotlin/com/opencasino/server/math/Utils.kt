package com.opencasino.server.math

import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

val mapType: Type get() = object : TypeToken<Map<String, Any>>() {}.type

@Deprecated("Use RandomnessService / OutcomeProvider for auditable provably-fair randomness")
@Suppress("DEPRECATION")
fun getRandomIndex(max: Int): Int = getRandomNumber(0, max)

@Deprecated("Use RandomnessService / OutcomeProvider for auditable provably-fair randomness")
fun getRandomNumber(
    min: Int,
    max: Int,
): Int = ((Math.random() * (max - min)) + min).toInt()

@Deprecated("Use RandomnessService / OutcomeProvider for auditable provably-fair randomness")
fun getRandomNumber(
    min: Double,
    max: Double,
): Double = ((Math.random() * (max - min)) + min)
