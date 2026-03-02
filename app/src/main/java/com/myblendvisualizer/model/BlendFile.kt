package com.myblendvisualizer.model

import android.net.Uri

data class BlendFile(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long?
)
