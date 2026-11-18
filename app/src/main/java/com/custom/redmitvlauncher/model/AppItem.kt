package com.custom.redmitvlauncher.model

import android.graphics.drawable.Drawable

data class AppItem(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    var position: Int = 0,
    var isHidden: Boolean = false
)
