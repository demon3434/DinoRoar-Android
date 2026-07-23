package com.example.dinoroar.network

import android.util.Log
import com.example.dinoroar.data.local.SecurePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val securePrefs: SecurePrefs,
    private val connectionManager: ConnectionManager,
    // Use Provider to avoid circular dependency since OkHttpClient will use this interceptor, 
    // and Retrofit DinoApiService might also need OkHttpClient.
    private val okHttpClientProvider: Provider<okhttp3.OkHttpClient>
) : Interceptor {
    
    private val TAG = "AuthInterceptor"
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()

        // 1. Inject Bearer token if present
        securePrefs.token?.let { token ->
            requestBuilder.header("Authorization", "Bearer $token")
        }

        var request = requestBuilder.build()

        // Dynamic base URL redirect
        securePrefs.serverUrl?.let { serverUrl ->
            request = applyRedirect(request, serverUrl)
        }

        var response: Response
        try {
            response = chain.proceed(request)
        } catch (e: java.io.IOException) {
            val intranetUrl = securePrefs.intranetUrl
            val extranetUrl = securePrefs.extranetUrl
            val currentServerUrl = securePrefs.serverUrl

            if (!intranetUrl.isNullOrBlank() && !extranetUrl.isNullOrBlank() &&
                currentServerUrl == intranetUrl && intranetUrl != extranetUrl) {
                
                Log.w(TAG, "Intranet request failed. Retrying and falling back to extranet URL: $extranetUrl")
                securePrefs.serverUrl = extranetUrl
                connectionManager.forceConnection(extranetUrl)

                val fallbackRequest = applyRedirect(request, extranetUrl)
                response = chain.proceed(fallbackRequest)
            } else {
                throw e
            }
        }

        // 2. Scan response body for lock_reset_flag = default_requested
        val contentType = response.body?.contentType()
        if (response.isSuccessful && contentType != null && contentType.toString().contains("application/json")) {
            try {
                // Peek the response body (max 1MB) so we don't consume the stream
                val responseBody = response.peekBody(1024 * 1024)
                val bodyString = responseBody.string()
                
                if (bodyString.contains("lock_reset_flag") && bodyString.contains("default_requested")) {
                    Log.w(TAG, "Detected lock_reset_flag = default_requested from server!")
                    
                    // Trigger local reset
                    securePrefs.resetLockToDefault()
                    
                    // Send reset confirmation back to server asynchronously
                    val serverUrl = securePrefs.serverUrl
                    val token = securePrefs.token
                    if (serverUrl != null && token != null) {
                        coroutineScope.launch {
                            confirmLockReset(serverUrl, token)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking lock_reset_flag in interceptor: ${e.message}")
            }
        }

        return response
    }

    private fun confirmLockReset(serverUrl: String, token: String) {
        val client = okHttpClientProvider.get()
        val jsonPayload = "{\"lock_pattern\":\"1,2,3\"}"
        val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
        
        val url = "${serverUrl.removeSuffix("/")}/api/auth/lock"
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Authorization", "Bearer $token")
            .build()
            
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.i(TAG, "Successfully confirmed lock reset on server.")
                } else {
                    Log.e(TAG, "Failed to confirm lock reset on server. Code: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during lock reset confirmation: ${e.message}")
        }
    }

    private fun applyRedirect(request: Request, baseUrl: String): Request {
        try {
            val parsedUrl = baseUrl.toHttpUrlOrNull()
            if (parsedUrl != null) {
                val newUrl = request.url.newBuilder()
                    .scheme(parsedUrl.scheme)
                    .host(parsedUrl.host)
                    .port(parsedUrl.port)
                    .build()
                return request.newBuilder().url(newUrl).build()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error redirecting request to $baseUrl: ${e.message}")
        }
        return request
    }
}
