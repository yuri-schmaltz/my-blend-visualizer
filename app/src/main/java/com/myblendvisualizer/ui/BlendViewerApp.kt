package com.myblendvisualizer.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myblendvisualizer.data.resolveBlendFile
import com.myblendvisualizer.model.BlendFile
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNode
import java.text.DecimalFormat

@Composable
fun BlendViewerApp(
    viewModel: BlendViewerViewModel = viewModel(factory = BlendViewerViewModel.Factory)
) {
    val context = LocalContext.current
    val uiState = viewModel.uiState
    var showInAppViewer by rememberSaveable { mutableStateOf(false) }

    val pickerLauncher = rememberLauncherForActivityResult(contract = OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some providers do not grant persistable permissions.
        }
        val blendFile = resolveBlendFile(
            contentResolver = context.contentResolver,
            uri = uri
        )
        if (blendFile == null) {
            viewModel.onInvalidFileSelected()
        } else {
            viewModel.onBlendSelected(blendFile)
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "My Blend Visualizer",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "MVP para Android: seleciona arquivo .blend e inicia fluxo de conversao para preview 3D.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Button(onClick = { pickerLauncher.launch(arrayOf("*/*")) }) {
                    Text("Selecionar arquivo .blend")
                }

                BlendFileCard(file = uiState.selectedFile)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = viewModel::convertSelectedBlend,
                        enabled = uiState.selectedFile != null &&
                            uiState.conversionStatus != ConversionStatus.Running
                    ) {
                        Text("Converter")
                    }
                    FilledTonalButton(
                        onClick = { showInAppViewer = true },
                        enabled = uiState.previewUri != null
                    ) {
                        Text("Visualizar no app")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            val previewUri = uiState.previewUri ?: return@Button
                            val intent = Intent(Intent.ACTION_VIEW, previewUri)
                            context.startActivity(intent)
                        },
                        enabled = uiState.previewUri != null
                    ) {
                        Text("Abrir externo")
                    }
                }

                StatusCard(message = uiState.statusMessage)
            }
        }
    }

    val previewUri = uiState.previewUri
    if (showInAppViewer && previewUri != null) {
        InAppGlbViewerDialog(
            modelUri = previewUri,
            onDismiss = { showInAppViewer = false }
        )
    }
}

@Composable
private fun BlendFileCard(file: BlendFile?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Arquivo", style = MaterialTheme.typography.titleMedium)
            if (file == null) {
                Text(
                    text = "Nenhum arquivo selecionado.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Tamanho: ${formatBytes(file.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun StatusCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Status", style = MaterialTheme.typography.titleMedium)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun formatBytes(value: Long?): String {
    if (value == null) return "desconhecido"
    if (value < 1024) return "$value B"
    val kb = value / 1024.0
    if (kb < 1024.0) return "${DecimalFormat("#.##").format(kb)} KB"
    val mb = kb / 1024.0
    if (mb < 1024.0) return "${DecimalFormat("#.##").format(mb)} MB"
    val gb = mb / 1024.0
    return "${DecimalFormat("#.##").format(gb)} GB"
}

@Composable
private fun InAppGlbViewerDialog(
    modelUri: Uri,
    onDismiss: () -> Unit
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val centerNode = rememberNode(engine)
    val cameraNode = rememberCameraNode(engine) {
        position = Position(z = 4.0f)
        lookAt(centerNode)
    }
    val modelUrl = modelUri.toString()
    var modelNode by remember(modelUrl) { mutableStateOf<ModelNode?>(null) }
    var modelLoadError by remember(modelUrl) { mutableStateOf<String?>(null) }

    LaunchedEffect(modelUrl) {
        modelNode = null
        modelLoadError = null
        val modelInstance = runCatching {
            modelLoader.loadModelInstance(modelUrl)
        }.getOrNull()
        if (modelInstance == null) {
            modelLoadError = "Falha ao carregar o modelo GLB."
        } else {
            modelNode = ModelNode(
                modelInstance = modelInstance,
                scaleToUnits = 2.0f,
                centerOrigin = Position(y = -0.5f)
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onDismiss) {
                    Text("Fechar")
                }
            }

            val loadedModelNode = modelNode
            if (loadedModelNode == null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(modelLoadError ?: "Carregando modelo 3D...")
                        Text(
                            text = "Tente abrir externamente ou verifique se a URL do GLB esta acessivel.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                Scene(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    engine = engine,
                    modelLoader = modelLoader,
                    cameraNode = cameraNode,
                    cameraManipulator = rememberCameraManipulator(
                        orbitHomePosition = cameraNode.worldPosition,
                        targetPosition = centerNode.worldPosition
                    ),
                    childNodes = listOf(centerNode, loadedModelNode)
                )
            }
        }
    }
}
