package com.pranavkd.instadown.data.remote.interceptor

import com.pranavkd.instadown.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

class AuthHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("x-rapidapi-key", BuildConfig.RAPID_API_KEY)
            .addHeader("x-rapidapi-host", BuildConfig.RAPID_API_HOST)
            .addHeader("Content-Type", "application/json")
            .build()
        return chain.proceed(request)
    }
}
