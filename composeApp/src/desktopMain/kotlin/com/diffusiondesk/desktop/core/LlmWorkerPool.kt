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
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class LlmWorkerStatus {
    Stopped,
    Starting,
    Loading,
    ReadyNoModel,
    Ready,
    Busy,
    Error,
}

data class LlmWorkerState(
    val id: String,
    val presetId: String,
    val presetName: String,
    val status: LlmWorkerStatus,
    val baseUrl: String,
    val executablePath: String = "",
    val modelPath: String = "",
    val placement: LlmPlacement = LlmPlacement.Cpu,
    val parsedArgs: List<String> = emptyList(),
    val nGpuLayers: Int = -1,
    val vramAllocatedMb: Int = 0,
    val vramFreeMb: Int = 0,
    val message: String = "",
    val lastLogLine: String = "",
)

data class LlmWorkerHandle(
    val id: String,
    val baseUrl: String,
    val preset: LlmPreset,
)

class LlmWorkerPool(
    private val scope: CoroutineScope,
    private val client: DiffusionDeskClient,
    private val internalToken: String,
) {
    private data class ManagedWorker(
        val signature: String,
        val preset: LlmPreset,
        val port: Int,
        val process: Process,
        val logJob: Job,
        val monitorJob: Job,
    )

    private val _state = MutableStateFlow<List<LlmWorkerState>>(emptyList())
    val state: StateFlow<List<LlmWorkerState>> = _state.asStateFlow()

    private val workers = mutableMapOf<String, ManagedWorker>()
    private val closing = AtomicBoolean(false)

    suspend fun ensureWorkerForPreset(settings: DesktopSettings, preset: LlmPreset): Result<LlmWorkerHandle> {
        return runCatching {
            check(!closing.get()) { "LLM worker pool is closing." }
            val parsedArgs = parsedArgsFor(preset)
            val signature = signatureFor(preset, parsedArgs)
            workers[signature]?.takeIf { it.process.isAlive }?.let { existing ->
                val existingState = _state.value.firstOrNull { it.id == signature }
                if (existingState?.status == LlmWorkerStatus.Ready) {
                    return@runCatching LlmWorkerHandle(signature, baseUrl(existing.port), existing.preset)
                }
                if (existingState?.status == LlmWorkerStatus.ReadyNoModel) {
                    loadExistingWorker(signature, existing, parsedArgs)
                    return@runCatching LlmWorkerHandle(signature, baseUrl(existing.port), existing.preset)
                }
            }

            workers[signature]?.let { stopManaged(signature, it, notify = false) }

            val executable = resolveExecutable(settings.repoRoot)
                ?: error("Could not find diffusion_desk_llm_worker under ${settings.repoRoot}\\build")
            val port = allocatePort(settings.listenPort + 1)
            val baseUrl = baseUrl(port)

            updateWorkerState(
                LlmWorkerState(
                    id = signature,
                    presetId = preset.id,
                    presetName = preset.name,
                    status = LlmWorkerStatus.Starting,
                    baseUrl = baseUrl,
                    executablePath = executable.absolutePath,
                    modelPath = preset.modelPath,
                    placement = preset.placement,
                    parsedArgs = parsedArgs,
                    message = "Starting LLM worker...",
                ),
            )

            val args = mutableListOf(
                executable.absolutePath,
                "--listen-port", port.toString(),
                "--listen-ip", "127.0.0.1",
                "--model-dir", settings.modelDir,
                "--llm-idle-timeout", "0",
                "--verbose",
            )
            if (internalToken.isNotBlank()) {
                args += listOf("--internal-token", internalToken)
            }

            val process = ProcessBuilder(args)
                .directory(File(settings.repoRoot))
                .redirectErrorStream(true)
                .start()

            if (closing.get()) {
                destroyProcess(process)
                error("LLM worker pool is closing.")
            }

            val logJob = watchLogs(signature, process)
            val monitorJob = monitorProcess(signature, process)
            val managed = ManagedWorker(signature, preset, port, process, logJob, monitorJob)
            workers[signature] = managed

            waitForHealth(signature, process, baseUrl)
            updateFromHealth(signature, baseUrl, status = LlmWorkerStatus.ReadyNoModel, message = "LLM worker ready.")
            loadExistingWorker(signature, managed, parsedArgs)

            LlmWorkerHandle(signature, baseUrl, preset)
        }.onFailure { error ->
            val signature = runCatching { signatureFor(preset, parsedArgsFor(preset)) }
                .getOrDefault("llm-${preset.id}")
            updateWorker(signature) {
                copy(
                    status = LlmWorkerStatus.Error,
                    message = error.message ?: "LLM worker startup failed.",
                )
            }
        }
    }

    suspend fun unloadPreset(presetId: String): Result<Unit> {
        val matches = workers.filterValues { it.preset.id == presetId }
        if (matches.isEmpty()) {
            return Result.success(Unit)
        }
        return runCatching {
            matches.forEach { (id, worker) ->
                client.unloadLlmModel(baseUrl(worker.port)).getOrThrow()
                updateFromHealth(
                    id = id,
                    baseUrl = baseUrl(worker.port),
                    status = LlmWorkerStatus.ReadyNoModel,
                    message = "Unloaded ${worker.preset.name}.",
                )
            }
        }
    }

    suspend fun unloadGpuModelsForImageGeneration(): Result<Unit> {
        val matches = workers.filterValues { worker ->
            worker.preset.placement != LlmPlacement.Cpu && worker.process.isAlive
        }
        if (matches.isEmpty()) {
            return Result.success(Unit)
        }

        return runCatching {
            matches.forEach { (id, worker) ->
                client.unloadLlmModel(baseUrl(worker.port)).getOrThrow()
                updateFromHealth(
                    id = id,
                    baseUrl = baseUrl(worker.port),
                    status = LlmWorkerStatus.ReadyNoModel,
                    message = "Unloaded ${worker.preset.name} for image generation.",
                )
            }
        }
    }

    suspend fun stopWorker(id: String): Result<Unit> {
        val worker = workers[id] ?: return Result.success(Unit)
        return runCatching {
            stopManaged(id, worker, notify = true)
        }
    }

    suspend fun stopAll() {
        workers.toMap().forEach { (id, worker) ->
            runCatching { stopManaged(id, worker, notify = true) }
        }
    }

    fun close() {
        closing.set(true)
        workers.toMap().forEach { (id, worker) ->
            worker.logJob.cancel()
            worker.monitorJob.cancel()
            runCatching { client.shutdownLlmWorkerBlocking(baseUrl(worker.port)) }
            destroyProcess(worker.process)
            workers.remove(id)
        }
        _state.value = emptyList()
    }

    private suspend fun stopManaged(id: String, worker: ManagedWorker, notify: Boolean) {
        worker.logJob.cancel()
        worker.monitorJob.cancel()
        runCatching { client.shutdownLlmWorker(baseUrl(worker.port)).getOrThrow() }
        destroyProcess(worker.process)
        workers.remove(id)
        if (notify) {
            updateWorker(id) {
                copy(
                    status = LlmWorkerStatus.Stopped,
                    message = "Stopped ${worker.preset.name}.",
                    lastLogLine = "",
                )
            }
        } else {
            removeWorkerState(id)
        }
    }

    private suspend fun waitForHealth(id: String, process: Process, baseUrl: String) {
        repeat(60) { attempt ->
            if (!process.isAlive) {
                error("LLM worker exited before becoming ready.")
            }
            if (client.verifyLlmWorker(baseUrl).isSuccess) {
                return
            }
            updateWorker(id) { copy(message = "Waiting for LLM worker startup... (${attempt + 1}/60)") }
            delay(1000)
        }
        error("LLM worker did not become ready in time.")
    }

    private suspend fun loadExistingWorker(id: String, worker: ManagedWorker, parsedArgs: List<String>) {
        val baseUrl = baseUrl(worker.port)
        updateWorker(id) {
            copy(
                status = LlmWorkerStatus.Loading,
                parsedArgs = parsedArgs,
                message = "Loading ${worker.preset.name}...",
            )
        }
        client.loadLlmPreset(baseUrl, worker.preset).getOrThrow()
        updateFromHealth(id, baseUrl, status = LlmWorkerStatus.Ready, message = "Loaded ${worker.preset.name}.")
    }

    private suspend fun updateFromHealth(
        id: String,
        baseUrl: String,
        status: LlmWorkerStatus,
        message: String,
    ) {
        val health = client.verifyLlmWorker(baseUrl).getOrNull()
        updateWorker(id) {
            copy(
                status = status,
                message = message,
                nGpuLayers = health?.nGpuLayers ?: nGpuLayers,
                vramAllocatedMb = health?.vramAllocatedMb ?: vramAllocatedMb,
                vramFreeMb = health?.vramFreeMb ?: vramFreeMb,
            )
        }
    }

    private fun watchLogs(id: String, process: Process): Job {
        return scope.launch(Dispatchers.IO) {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    updateWorker(id) { copy(lastLogLine = line) }
                }
            }
        }
    }

    private fun monitorProcess(id: String, process: Process): Job {
        return scope.launch(Dispatchers.IO) {
            val exitCode = process.waitFor()
            if (workers[id]?.process == process) {
                workers.remove(id)
                updateWorker(id) {
                    copy(
                        status = LlmWorkerStatus.Error,
                        message = "LLM worker exited with code $exitCode.",
                    )
                }
            }
        }
    }

    private fun allocatePort(startPort: Int): Int {
        val used = workers.values.map { it.port }.toSet()
        var port = startPort
        while (port in used || !isPortAvailable(port)) {
            port += 1
        }
        return port
    }

    private fun isPortAvailable(port: Int): Boolean {
        return runCatching {
            ServerSocket().use { socket ->
                socket.reuseAddress = false
                socket.bind(InetSocketAddress("127.0.0.1", port))
            }
        }.isSuccess
    }

    private fun resolveExecutable(repoRoot: String): File? {
        val candidates = listOf(
            File(repoRoot, "build/bin/diffusion_desk_llm_worker.exe"),
            File(repoRoot, "build/diffusion_desk_llm_worker.exe"),
            File(repoRoot, "build/bin/Debug/diffusion_desk_llm_worker.exe"),
            File(repoRoot, "build/Debug/diffusion_desk_llm_worker.exe"),
            File(repoRoot, "build/bin/diffusion_desk_llm_worker"),
            File(repoRoot, "build/diffusion_desk_llm_worker"),
        )
        return candidates.firstOrNull { it.exists() }
    }

    private fun parsedArgsFor(preset: LlmPreset): List<String> {
        return preset.effectiveAdvancedArgs().getOrThrow()
    }

    private fun signatureFor(preset: LlmPreset, args: List<String>): String {
        return listOf(preset.modelPath, preset.mmprojPath, preset.placement.name, args.joinToString("\u001f"))
            .joinToString("\u001e")
            .hashCode()
            .toUInt()
            .toString(16)
            .let { "llm-$it" }
    }

    private fun baseUrl(port: Int): String = "http://127.0.0.1:$port"

    private fun destroyProcess(process: Process) {
        if (process.isAlive) {
            process.destroy()
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
        }
    }

    private fun updateWorkerState(workerState: LlmWorkerState) {
        _state.value = _state.value
            .filterNot { it.id == workerState.id }
            .plus(workerState)
    }

    private fun updateWorker(id: String, transform: LlmWorkerState.() -> LlmWorkerState) {
        _state.value = _state.value.map { state ->
            if (state.id == id) state.transform() else state
        }
    }

    private fun removeWorkerState(id: String) {
        _state.value = _state.value.filterNot { it.id == id }
    }
}
