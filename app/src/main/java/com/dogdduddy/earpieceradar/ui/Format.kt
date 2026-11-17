package com.dogdduddy.earpieceradar.ui

import java.util.Locale

fun Double.f(digits: Int = 2): String = String.format(Locale.US, "%.${digits}f", this)
fun Float.f(digits: Int = 2): String = String.format(Locale.US, "%.${digits}f", this)
