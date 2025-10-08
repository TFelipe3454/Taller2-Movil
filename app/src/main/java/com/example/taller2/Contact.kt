package com.example.taller2

import android.graphics.Bitmap

data class Contact(
    val name: String,
    val number: String,
    val image: Bitmap? = null
)

