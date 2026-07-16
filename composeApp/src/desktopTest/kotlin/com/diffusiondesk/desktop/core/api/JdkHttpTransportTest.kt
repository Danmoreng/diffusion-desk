package com.diffusiondesk.desktop.core.api

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JdkHttpTransportTest {
    private lateinit var server: HttpServer
    private lateinit var baseUrl: String

    @BeforeTest
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        baseUrl = "http://127.0.0.1:${server.address.port}"
        server.start()
    }

    @AfterTest
    fun stopServer() = server.stop(0)

    @Test
    fun sendsGetPostHeadersAndBodies() {
        server.createContext("/request") { exchange ->
            assertEquals("secret", exchange.requestHeaders.getFirst("X-Internal-Token"))
            val response = "${exchange.requestMethod}:${exchange.requestBody.readBytes().decodeToString()}"
            exchange.sendResponseHeaders(200, response.length.toLong())
            exchange.responseBody.use { it.write(response.encodeToByteArray()) }
        }
        val transport = JdkHttpTransport("secret")

        assertEquals("GET:", transport.text(request("/request").build()).body())
        assertEquals("POST:payload", transport.text(request("/request").POST("payload").build()).body())
    }

    @Test
    fun retainsNonSuccessResponseBodiesInErrors() {
        server.createContext("/error") { exchange ->
            val body = "request failed"
            exchange.sendResponseHeaders(500, body.length.toLong())
            exchange.responseBody.use { it.write(body.encodeToByteArray()) }
        }

        val error = assertFailsWith<IllegalStateException> { JdkHttpTransport().text(request("/error").build()) }
        assertContains(error.message.orEmpty(), "request failed")
    }

    @Test
    fun supportsByteStreamDiscardAndTimeoutResponses() {
        server.createContext("/bytes") { exchange ->
            val body = byteArrayOf(1, 2, 3)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/stream") { exchange ->
            val body = "stream"
            exchange.sendResponseHeaders(200, body.length.toLong())
            exchange.responseBody.use { it.write(body.encodeToByteArray()) }
        }
        server.createContext("/discard") { exchange -> exchange.sendResponseHeaders(204, -1); exchange.close() }
        server.createContext("/slow") { exchange ->
            Thread.sleep(100)
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
        }
        val transport = JdkHttpTransport()

        assertEquals(listOf<Byte>(1, 2, 3), transport.bytes(request("/bytes").build()).body().toList())
        assertEquals("stream", transport.stream(request("/stream").build()).body().use { it.readBytes().decodeToString() })
        transport.discard(request("/discard").POST().build())
        assertFailsWith<java.net.http.HttpTimeoutException> {
            transport.text(request("/slow").timeout(Duration.ofMillis(10)).build())
        }
    }

    private fun request(path: String) = HttpRequestSpecBuilder(URI.create("$baseUrl$path"))
}
