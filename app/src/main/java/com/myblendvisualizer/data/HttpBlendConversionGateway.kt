package com.myblendvisualizer.data

import android.content.ContentResolver
import android.util.Log
import com.myblendvisualizer.model.BlendFile
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class HttpBlendConversionGateway(
    private val contentResolver: ContentResolver,
    private val cacheDir: File,
    private val baseUrl: String,
    private val apiKey: String = "",
    private val maxRetries: Int = 2,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build()
) : BlendConversionGateway {

    override suspend fun convertToPreviewModel(file: BlendFile): BlendConversionResult {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        if (normalizedBaseUrl.isBlank()) {
            return BlendConversionResult.Error("URL do backend nao configurada.")
        }

        val tempInputFile = File(cacheDir, "blend-upload-${UUID.randomUUID()}.blend")
        return try {
            copyUriToTempFile(file, tempInputFile)
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    name = "file",
                    filename = file.displayName,
                    body = tempInputFile.asRequestBody("application/octet-stream".toMediaType())
                )
                .build()

            val initialRequest = Request.Builder()
                .url("$normalizedBaseUrl/convert")
                .post(multipartBody)
                .apply {
                    if (apiKey.isNotBlank()) {
                        addHeader("X-API-Key", apiKey)
                    }
                }
                .build()

            val taskId = httpClient.newCall(initialRequest).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val apiError = parseErrorMessage(payload)
                    return BlendConversionResult.Error(apiError ?: "Falha no backend (${response.code}).")
                }
                parseTaskId(payload) ?: return BlendConversionResult.Error("Resposta invalida: task_id ausente.")
            }

            // Polling loop
            var pollAttempt = 0
            val maxPollAttempts = 60 // 2 minutes with 2s interval
            
            while (pollAttempt < maxPollAttempts) {
                pollAttempt++
                delay(2000)

                val statusRequest = Request.Builder()
                    .url("$normalizedBaseUrl/status/$taskId")
                    .get()
                    .apply {
                        if (apiKey.isNotBlank()) {
                            addHeader("X-API-Key", apiKey)
                        }
                    }
                    .build()

                httpClient.newCall(statusRequest).execute().use { response ->
                    val payload = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return BlendConversionResult.Error("Falha ao consultar status (${response.code}).")
                    }

                    val json = JSONObject(payload)
                    val status = json.optString("status")
                    
                    when (status) {
                        "completed" -> {
                            val modelUrl = json.optString("model_url")
                            return if (modelUrl.isNotBlank()) {
                                BlendConversionResult.Success(android.net.Uri.parse(modelUrl))
                            } else {
                                BlendConversionResult.Error("URL do modelo ausente na resposta.")
                            }
                        }
                        "failed" -> {
                            val error = json.optString("error")
                            return BlendConversionResult.Error(error.ifBlank { "Falha na conversao." })
                        }
                        "processing", "pending" -> {
                            Log.d(TAG, "Conversao em andamento ($status)... Tentativa $pollAttempt")
                        }
                        else -> {
                            return BlendConversionResult.Error("Status inesperado: $status")
                        }
                    }
                }
            }

            BlendConversionResult.Error("Tempo limite de conversao excedido.")
        } catch (exception: Exception) {
            BlendConversionResult.Error("Falha na conversao: ${exception.message ?: "desconhecida"}")
        } finally {
            tempInputFile.delete()
        }
    }

    private fun parseTaskId(payload: String): String? {
        if (payload.isBlank()) return null
        return runCatching { JSONObject(payload).optString("task_id") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun copyUriToTempFile(file: BlendFile, destination: File) {
        val inputStream = contentResolver.openInputStream(file.uri)
            ?: throw IOException("Nao foi possivel abrir o arquivo selecionado.")
        inputStream.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun parseErrorMessage(payload: String): String? {
        if (payload.isBlank()) return null
        return runCatching {
            val json = JSONObject(payload)
            json.optString("detail").ifBlank { json.optString("message") }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val TAG = "HttpBlendGateway"
    }
}
