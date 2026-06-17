package com.komgareader.plugin.calibre.client

import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds HTTP Basic auth when a username is configured (Calibre Content Server run with
 * --enable-auth). With a blank username the request passes through unchanged.
 */
internal class CalibreAuthInterceptor(
    private val username: String,
    private val password: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val authed = if (username.isNotBlank()) {
            request.newBuilder()
                .header("Authorization", Credentials.basic(username, password))
                .build()
        } else {
            request
        }
        return chain.proceed(authed)
    }
}
