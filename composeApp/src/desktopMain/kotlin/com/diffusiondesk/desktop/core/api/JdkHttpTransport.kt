package com.diffusiondesk.desktop.core.api

import java.io.InputStream
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse as JdkHttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

class JdkHttpTransport(
    private val internalToken: String = "",
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build(),
) : HttpTransport {
    override fun text(request: HttpRequestSpec): HttpTransportResponse<String> {
        val response = httpClient.send(request.toJdkRequest(), JdkHttpResponse.BodyHandlers.ofString())
        validate(response.statusCode(), response.body())
        return HttpTransportResponse(response.body(), response.statusCode())
    }

    override fun bytes(request: HttpRequestSpec): HttpTransportResponse<ByteArray> {
        val response = httpClient.send(request.toJdkRequest(), JdkHttpResponse.BodyHandlers.ofByteArray())
        validate(response.statusCode(), response.body().toString(StandardCharsets.UTF_8))
        return HttpTransportResponse(response.body(), response.statusCode())
    }

    override fun stream(request: HttpRequestSpec): HttpTransportResponse<InputStream> {
        val response = httpClient.send(request.toJdkRequest(), JdkHttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) {
            response.body().use { body ->
                validate(response.statusCode(), body.readBytes().toString(StandardCharsets.UTF_8))
            }
        }
        return HttpTransportResponse(response.body(), response.statusCode())
    }

    override fun discard(request: HttpRequestSpec): HttpTransportResponse<Unit> {
        val response = httpClient.send(request.toJdkRequest(), JdkHttpResponse.BodyHandlers.discarding())
        validate(response.statusCode(), "")
        return HttpTransportResponse(Unit, response.statusCode())
    }

    private fun HttpRequestSpec.toJdkRequest(): HttpRequest = HttpRequest.newBuilder(uri).apply {
        if (internalToken.isNotBlank()) {
            header("X-Internal-Token", internalToken)
        }
        headers.forEach(::header)
        timeout?.let(::timeout)
        when (method) {
            "GET" -> GET()
            "POST" -> POST(body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
            else -> method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
        }
    }.build()

    private fun validate(statusCode: Int, body: String) {
        check(statusCode in 200..299) {
            body.ifBlank { "HTTP request failed with $statusCode" }
        }
    }
}
