package com.myblendvisualizer.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myblendvisualizer.data.BlendConversionGateway
import com.myblendvisualizer.data.BlendConversionResult
import com.myblendvisualizer.data.HttpBlendConversionGateway
import com.myblendvisualizer.data.MockBlendConversionGateway
import com.myblendvisualizer.BuildConfig
import com.myblendvisualizer.model.BlendFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ConversionStatus {
    Idle,
    Running,
    Success,
    Error
}

data class BlendViewerUiState(
    val selectedFile: BlendFile? = null,
    val conversionStatus: ConversionStatus = ConversionStatus.Idle,
    val previewUri: Uri? = null,
    val statusMessage: String = "Selecione um arquivo .blend para iniciar."
)

class BlendViewerViewModel(
    private val conversionGateway: BlendConversionGateway,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    var uiState by mutableStateOf(BlendViewerUiState())
        private set

    fun onBlendSelected(file: BlendFile) {
        uiState = uiState.copy(
            selectedFile = file,
            conversionStatus = ConversionStatus.Idle,
            previewUri = null,
            statusMessage = "Arquivo carregado. Toque em Converter para gerar o preview."
        )
    }

    fun onInvalidFileSelected() {
        uiState = uiState.copy(
            selectedFile = null,
            conversionStatus = ConversionStatus.Error,
            previewUri = null,
            statusMessage = "O arquivo selecionado nao parece ser .blend."
        )
    }

    fun convertSelectedBlend() {
        val file = uiState.selectedFile ?: return
        uiState = uiState.copy(
            conversionStatus = ConversionStatus.Running,
            previewUri = null,
            statusMessage = "Convertendo ${file.displayName}..."
        )

        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                conversionGateway.convertToPreviewModel(file)
            }
            uiState = when (result) {
                is BlendConversionResult.Success -> {
                    uiState.copy(
                        conversionStatus = ConversionStatus.Success,
                        previewUri = result.modelUri,
                        statusMessage = "Preview pronto: ${result.modelUri}"
                    )
                }

                is BlendConversionResult.Error -> {
                    uiState.copy(
                        conversionStatus = ConversionStatus.Error,
                        previewUri = null,
                        statusMessage = result.reason
                    )
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                val gateway: BlendConversionGateway = if (application != null) {
                    HttpBlendConversionGateway(
                        contentResolver = application.contentResolver,
                        cacheDir = application.cacheDir,
                        baseUrl = BuildConfig.CONVERTER_BASE_URL,
                        apiKey = BuildConfig.CONVERTER_API_KEY
                    )
                } else {
                    MockBlendConversionGateway()
                }
                BlendViewerViewModel(
                    conversionGateway = gateway
                )
            }
        }
    }
}
