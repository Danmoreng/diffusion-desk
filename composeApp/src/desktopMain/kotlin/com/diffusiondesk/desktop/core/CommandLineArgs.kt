package com.diffusiondesk.desktop.core

object CommandLineArgs {
    private val reservedOptions = setOf(
        "--listen-ip",
        "--listen-port",
        "--host",
        "--port",
        "--model-dir",
        "--internal-token",
        "--mode",
        "-m",
        "--model",
        "-lm",
        "--llm-model",
    )

    fun parse(value: String): Result<List<String>> = runCatching {
        val args = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaping = false

        fun flush() {
            if (current.isNotEmpty()) {
                args += current.toString()
                current.clear()
            }
        }

        value.forEach { char ->
            when {
                escaping -> {
                    current.append(char)
                    escaping = false
                }
                char == '\\' && quote != null -> escaping = true
                quote != null && char == quote -> quote = null
                quote != null -> current.append(char)
                char == '\'' || char == '"' -> quote = char
                char.isWhitespace() -> flush()
                else -> current.append(char)
            }
        }

        require(!escaping) { "Advanced arguments end with an unfinished escape." }
        require(quote == null) { "Advanced arguments contain an unclosed quote." }
        flush()
        args
    }

    fun validateNoReservedOptions(args: List<String>): Result<Unit> = runCatching {
        val reserved = args.firstOrNull { token ->
            val option = token.substringBefore("=")
            option in reservedOptions
        }
        require(reserved == null) {
            "Advanced arguments cannot include app-managed option $reserved."
        }
    }
}
