package com.example.qz1sample

private const val KILOMETERS_PER_NAUTICAL_MILE = 1.852

fun knotsToKilometersPerHour(knots: Double): Double {
    return knots * KILOMETERS_PER_NAUTICAL_MILE
}
