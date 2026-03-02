package com.myblendvisualizer.data

import com.myblendvisualizer.model.BlendFile
import kotlinx.coroutines.delay

class MockBlendConversionGateway : BlendConversionGateway {
    override suspend fun convertToPreviewModel(file: BlendFile): BlendConversionResult {
        delay(1100)
        return BlendConversionResult.Error(
            "Conversao nao configurada. O app precisa de um backend para transformar .blend em .glb/.usdz antes da renderizacao."
        )
    }
}
