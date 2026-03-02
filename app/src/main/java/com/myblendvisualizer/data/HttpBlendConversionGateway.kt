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

            var attempt = 0
            while (attempt <= maxRetries) {
                attempt += 1
                try {
                    val request = Request.Builder()
                        .url("$normalizedBaseUrl/convert")
                        .post(multipartBody)
                        .apply {
                            if (apiKey.isNotBlank()) {
                                addHeader("X-API-Key", apiKey)
                            }
                        }
                        .build()

                    var shouldRetry = false
                    var result: BlendConversionResult? = null

                    httpClient.newCall(request).execute().use { response ->
                        val payload = response.body?.string().orEmpty()
                        if (response.isSuccessful) {
                            val modelUrl = parseModelUrl(payload)
                            if (modelUrl.isNullOrBlank()) {
                                result = BlendConversionResult.Error(
                                    "Resposta invalida do backend: model_url ausente."
                                )
                            } else {
                                result = BlendConversionResult.Success(
                                    modelUri = android.net.Uri.parse(modelUrl)
                                )
                            }
                            return@use
                        }

                        if (response.code in 500..599 && attempt <= maxRetries) {
                            shouldRetry = true
                            Log.w(TAG, "Tentativa $attempt falhou com ${response.code}. Retentando...")
                            return@use
                        }

                        val apiError = parseErrorMessage(payload)
                        result = BlendConversionResult.Error(
                            apiError ?: "Falha no backend (${response.code})."
                        )
                    }

                    if (shouldRetry) {
                        delay(backoffDelayMs(attempt))
                        continue
                    }
                    return result ?: BlendConversionResult.Error(
                        "Falha inesperada na comunicacao com o backend."
                    )
                } catch (ioException: IOException) {
                    if (attempt <= maxRetries) {
                        Log.w(TAG, "Erro de rede na tentativa $attempt: ${ioException.message}")
                        delay(backoffDelayMs(attempt))
                        continue
                    }
                    return BlendConversionResult.Error(
                        "Erro de rede: ${ioException.message ?: "desconhecido"}"
                    )
                }
            }
            BlendConversionResult.Error("Nao foi possivel concluir a conversao apos varias tentativas.")
        } catch (exception: Exception) {
            BlendConversionResult.Error("Falha na conversao: ${exception.message ?: "desconhecida"}")
        } finally {
            tempInputFile.delete()
        }
    }

    private fun backoffDelayMs(attempt: Int): Long {
        return when (attempt) {
            1 -> 500L
            2 -> 1200L
            else -> 2000L
        }
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

    private fun parseModelUrl(payload: String): String? {
        if (payload.isBlank()) return null
        return runCatching { JSONObject(payload).optString("model_url") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
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
