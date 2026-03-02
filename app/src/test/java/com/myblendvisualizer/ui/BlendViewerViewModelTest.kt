package com.myblendvisualizer.ui

import android.net.Uri
import com.myblendvisualizer.data.BlendConversionGateway
import com.myblendvisualizer.data.BlendConversionResult
import com.myblendvisualizer.model.BlendFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BlendViewerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `onBlendSelected atualiza estado inicial da conversao`() {
        val viewModel = BlendViewerViewModel(
            conversionGateway = FakeGateway(
                BlendConversionResult.Error("nao usado")
            ),
            ioDispatcher = Dispatchers.Main
        )
        val file = sampleBlendFile("scene.blend")

        viewModel.onBlendSelected(file)

        assertEquals(file, viewModel.uiState.selectedFile)
        assertEquals(ConversionStatus.Idle, viewModel.uiState.conversionStatus)
        assertNull(viewModel.uiState.previewUri)
    }

    @Test
    fun `convertSelectedBlend com sucesso define previewUri`() = runTest {
        val previewUri = Uri.parse("https://example.com/model.glb")
        val viewModel = BlendViewerViewModel(
            conversionGateway = FakeGateway(
                BlendConversionResult.Success(previewUri)
            ),
            ioDispatcher = Dispatchers.Main
        )
        viewModel.onBlendSelected(sampleBlendFile("ok.blend"))

        viewModel.convertSelectedBlend()
        advanceUntilIdle()

        assertEquals(ConversionStatus.Success, viewModel.uiState.conversionStatus)
        assertEquals(previewUri, viewModel.uiState.previewUri)
        assertEquals("Preview pronto: $previewUri", viewModel.uiState.statusMessage)
    }

    @Test
    fun `convertSelectedBlend com erro limpa preview e exibe mensagem`() = runTest {
        val viewModel = BlendViewerViewModel(
            conversionGateway = FakeGateway(
                BlendConversionResult.Error("falha de backend")
            ),
            ioDispatcher = Dispatchers.Main
        )
        viewModel.onBlendSelected(sampleBlendFile("bad.blend"))

        viewModel.convertSelectedBlend()
        advanceUntilIdle()

        assertEquals(ConversionStatus.Error, viewModel.uiState.conversionStatus)
        assertNull(viewModel.uiState.previewUri)
        assertEquals("falha de backend", viewModel.uiState.statusMessage)
    }

    @Test
    fun `convertSelectedBlend sem arquivo selecionado nao altera estado`() = runTest {
        val viewModel = BlendViewerViewModel(
            conversionGateway = FakeGateway(
                BlendConversionResult.Error("nao usado")
            ),
            ioDispatcher = Dispatchers.Main
        )
        val initial = viewModel.uiState

        viewModel.convertSelectedBlend()
        advanceUntilIdle()

        assertEquals(initial, viewModel.uiState)
    }

    @Test
    fun `onInvalidFileSelected define estado de erro`() {
        val viewModel = BlendViewerViewModel(
            conversionGateway = FakeGateway(
                BlendConversionResult.Error("nao usado")
            ),
            ioDispatcher = Dispatchers.Main
        )
        viewModel.onBlendSelected(sampleBlendFile("ok.blend"))

        viewModel.onInvalidFileSelected()

        assertNull(viewModel.uiState.selectedFile)
        assertEquals(ConversionStatus.Error, viewModel.uiState.conversionStatus)
        assertNotNull(viewModel.uiState.statusMessage)
    }

    private fun sampleBlendFile(name: String): BlendFile {
        return BlendFile(
            uri = Uri.parse("content://tests/$name"),
            displayName = name,
            sizeBytes = 1024L
        )
    }

    private class FakeGateway(
        private val result: BlendConversionResult
    ) : BlendConversionGateway {
        override suspend fun convertToPreviewModel(file: BlendFile): BlendConversionResult {
            return result
        }
    }
}
