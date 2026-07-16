package com.pranavkd.instadown.data.download

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadEngine @Inject constructor(
    private val client: OkHttpClient
) {

    data class Progress(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val isComplete: Boolean
    )

    suspend fun download(
        remoteUrl: String,
        stagingFile: File,
        startOffset: Long = 0,
        onProgress: (Progress) -> Unit
    ): Result<Long> {
        return runCatching {
            val request = Request.Builder()
                .url(remoteUrl)
                .apply {
                    if (startOffset > 0) {
                        addHeader("Range", "bytes=$startOffset-")
                    }
                }
                .build()

            val response = client.newCall(request).await()
            if (!response.isSuccessful && response.code != 206) {
                throw IOException("Download failed: HTTP ${response.code}")
            }

            val body = response.body ?: throw IOException("Empty response body")
            val contentLength = body.contentLength()
            val totalBytes = if (startOffset > 0 && contentLength >= 0) {
                startOffset + contentLength
            } else {
                contentLength.coerceAtLeast(0)
            }

            stagingFile.parentFile?.mkdirs()
            stagingFile.appendBytes(ByteArray(0))

            val outputStream = stagingFile.outputStream()
            if (startOffset > 0) {
                outputStream.channel.position(startOffset)
            }

            var bytesWritten = startOffset
            val buffer = ByteArray(8192)
            var bytesRead: Int

            body.byteStream().use { input ->
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    bytesWritten += bytesRead
                    onProgress(Progress(
                        downloadedBytes = bytesWritten,
                        totalBytes = totalBytes,
                        isComplete = false
                    ))
                }
            }

            outputStream.close()

            onProgress(Progress(
                downloadedBytes = bytesWritten,
                totalBytes = totalBytes,
                isComplete = true
            ))

            bytesWritten
        }
    }

    private suspend fun okhttp3.Call.await(): okhttp3.Response {
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            enqueue(object : okhttp3.Callback {
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    continuation.resume(response) {}
                }

                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    continuation.resumeWith(Result.failure(e))
                }
            })
            continuation.invokeOnCancellation {
                try {
                    cancel()
                } catch (_: Exception) {}
            }
        }
    }

    private fun File.appendBytes(bytes: ByteArray) {
        if (!exists()) {
            parentFile?.mkdirs()
            createNewFile()
        }
    }
}
