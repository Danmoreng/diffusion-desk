package com.diffusiondesk.desktop.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

enum class BackendStatus {
    Stopped,
    Starting,
    Ready,
    Error,
}

data class BackendUiState(
    val status: BackendStatus = BackendStatus.Stopped,
    val baseUrl: String = "http://127.0.0.1:1234",
    val executablePath: String = "",
    val message: String = "Backend not started.",
    val lastLogLine: String = "",
)

class BackendManager(
    private val scope: CoroutineScope,
    private val client: DiffusionDeskClient,
) {
    private val _state = MutableStateFlow(BackendUiState())
    val state: StateFlow<BackendUiState> = _state.asStateFlow()

    private var process: Process? = null
    private var logJob: Job? = null

    suspend fun start(settings: DesktopSettings): Result<Unit> {
        if (process?.isAlive == true && _state.value.status == BackendStatus.Ready) {
            return Result.success(Unit)
        }

        val baseUrl = "http://127.0.0.1:${settings.listenPort}"
        if (client.fetchConfig(baseUrl).isSuccess) {
            _state.value = _state.value.copy(
                status = BackendStatus.Ready,
                baseUrl = baseUrl,
                message = "Connected to existing backend on $baseUrl",
            )
            return Result.success(Unit)
        }

        stop()

        val executable = resolveServerExecutable(settings.repoRoot)
            ?: return Result.failure(IllegalStateException("Could not find diffusion_desk_server.exe under ${settings.repoRoot}\\build"))

        _state.value = _state.value.copy(
            status = BackendStatus.Starting,
            executablePath = executable.absolutePath,
            baseUrl = baseUrl,
            message = "Starting backend...",
        )

        return runCatching {
            val newProcess = ProcessBuilder(
                executable.absolutePath,
                "--llm-idle-timeout", "600",
                "--listen-port", settings.listenPort.toString(),
                "--verbose",
            )
                .directory(File(settings.repoRoot))
                .redirectErrorStream(true)
                .start()

            process = newProcess
            watchLogs(newProcess)

            var ready = false
            repeat(60) { attempt ->
                if (!newProcess.isAlive) {
                    throw IllegalStateException("Backend exited before becoming ready.")
                }

                val configResult = client.fetchConfig(baseUrl)
                if (configResult.isSuccess) {
                    _state.value = _state.value.copy(
                        status = BackendStatus.Ready,
                        message = "Backend ready on $baseUrl",
                        baseUrl = baseUrl,
                    )
                    ready = true
                    return@repeat
                }

                _state.value = _state.value.copy(message = "Waiting for backend startup... (${attempt + 1}/60)")
                delay(1000)
            }

            if (ready) {
                Unit
            } else {
                throw IllegalStateException("Backend did not become ready in time.")
            }
        }.onFailure { error ->
            _state.value = _state.value.copy(
                status = BackendStatus.Error,
                message = error.message ?: "Backend startup failed.",
            )
        }
    }

    suspend fun stop() {
        logJob?.cancel()
        logJob = null

        process?.let { running ->
            if (running.isAlive) {
                running.destroy()
                if (!running.waitFor(3, TimeUnit.SECONDS)) {
                    running.destroyForcibly()
                }
            }
        }
        process = null

        _state.value = _state.value.copy(
            status = BackendStatus.Stopped,
            message = "Backend stopped.",
            lastLogLine = "",
        )
    }

    fun close() {
        logJob?.cancel()
        logJob = null
        process?.destroy()
        process = null
    }

    private fun watchLogs(process: Process) {
        logJob?.cancel()
        logJob = scope.launch(Dispatchers.IO) {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    _state.value = _state.value.copy(lastLogLine = line)
                }
            }
        }
    }

    private fun resolveServerExecutable(repoRoot: String): File? {
        val candidates = listOf(
            File(repoRoot, "build/bin/diffusion_desk_server.exe"),
            File(repoRoot, "build/diffusion_desk_server.exe"),
            File(repoRoot, "build/bin/Debug/diffusion_desk_server.exe"),
            File(repoRoot, "build/Debug/diffusion_desk_server.exe"),
        )
        return candidates.firstOrNull { it.exists() }
    }
}
