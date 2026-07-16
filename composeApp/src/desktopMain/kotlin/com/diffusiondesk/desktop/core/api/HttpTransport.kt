package com.diffusiondesk.desktop.core.api

import java.io.InputStream
import java.net.URI
import java.time.Duration

data class HttpRequestSpec(
    val uri: URI,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val timeout: Duration? = null,
)

class HttpRequestSpecBuilder internal constructor(private val uri: URI) {
    private var method = "GET"
    private val headers = linkedMapOf<String, String>()
    private var body: String? = null
    private var timeout: Duration? = null

    fun header(name: String, value: String) = apply { headers[name] = value }

    fun GET() = apply { method = "GET"; body = null }

    fun POST(value: String? = null) = apply { method = "POST"; body = value }

    fun timeout(value: Duration) = apply { timeout = value }

    fun build() = HttpRequestSpec(uri, method, headers, body, timeout)
}

class HttpTransportResponse<T>(
    private val responseBody: T,
    private val responseStatusCode: Int,
) {
    fun body(): T = responseBody

    fun statusCode(): Int = responseStatusCode
}

interface HttpTransport {
    fun text(request: HttpRequestSpec): HttpTransportResponse<String>

    fun bytes(request: HttpRequestSpec): HttpTransportResponse<ByteArray>

    fun stream(request: HttpRequestSpec): HttpTransportResponse<InputStream>

    fun discard(request: HttpRequestSpec): HttpTransportResponse<Unit>
}

sealed interface HttpResponseBodyHandler<T> {
    data object Text : HttpResponseBodyHandler<String>
    data object Bytes : HttpResponseBodyHandler<ByteArray>
    data object Stream : HttpResponseBodyHandler<InputStream>
    data object Discard : HttpResponseBodyHandler<Unit>
}

object HttpResponse {
    object BodyHandlers {
        fun ofString(): HttpResponseBodyHandler<String> = HttpResponseBodyHandler.Text

        fun ofByteArray(): HttpResponseBodyHandler<ByteArray> = HttpResponseBodyHandler.Bytes

        fun ofInputStream(): HttpResponseBodyHandler<InputStream> = HttpResponseBodyHandler.Stream

        fun discarding(): HttpResponseBodyHandler<Unit> = HttpResponseBodyHandler.Discard
    }
}

class HttpTransportClient(private val transport: HttpTransport) {
    @Suppress("UNCHECKED_CAST")
    fun <T> send(request: HttpRequestSpec, handler: HttpResponseBodyHandler<T>): HttpTransportResponse<T> = when (handler) {
        HttpResponseBodyHandler.Text -> transport.text(request) as HttpTransportResponse<T>
        HttpResponseBodyHandler.Bytes -> transport.bytes(request) as HttpTransportResponse<T>
        HttpResponseBodyHandler.Stream -> transport.stream(request) as HttpTransportResponse<T>
        HttpResponseBodyHandler.Discard -> transport.discard(request) as HttpTransportResponse<T>
    }
}
