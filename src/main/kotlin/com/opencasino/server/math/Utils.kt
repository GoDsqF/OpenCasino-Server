package com.opencasino.server.math

import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

val mapType: Type get() = object : TypeToken<Map<String, Any>>() {}.type

fun getRandomIndex(max: Int): Int = getRandomNumber(0, max)

fun getRandomNumber(
    min: Int,
    max: Int,
): Int = ((Math.random() * (max - min)) + min).toInt()

fun getRandomNumber(
    min: Double,
    max: Double,
): Double = ((Math.random() * (max - min)) + min)
