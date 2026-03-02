package com.myblendvisualizer.data

import android.net.Uri
import com.myblendvisualizer.model.BlendFile

interface BlendConversionGateway {
    suspend fun convertToPreviewModel(file: BlendFile): BlendConversionResult
}

sealed interface BlendConversionResult {
    data class Success(val modelUri: Uri) : BlendConversionResult
    data class Error(val reason: String) : BlendConversionResult
}
