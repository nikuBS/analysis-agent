package com.nikuagent.service

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Claude Code CLI(`claude`)를 subprocess로 실행하는 LLM 클라이언트.
 *
 * 프롬프트는 stdin으로 전달한다 (`claude --print`는 인수 없이 실행 후 stdin에서 읽음).
 * claude CLI가 로그인된 상태여야 동작한다.
 *
 * @param binaryPath  claude 실행 파일 경로 (예: /usr/local/bin/claude)
 * @param model       사용할 모델 (null이면 CLI 기본값 사용)
 */
class CliLlmClient(
    private val binaryPath: String,
    private val model: String? = null,
) : LlmClient {

    override fun complete(prompt: String, onChunk: ((String) -> Unit)?): String {
        val cmd = buildList {
            add(binaryPath)
            add("--print")
            if (!model.isNullOrBlank()) {
                add("--model")
                add(model)
            }
            // 프롬프트는 인수가 아닌 stdin으로 전달 (긴 프롬프트 안정성 + 특수문자 이슈 방지)
        }

        val process = try {
            ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            throw LlmException(
                "Claude CLI 실행 실패: ${e.message}\n" +
                "› claude CLI가 설치되어 있는지 확인해주세요. (https://claude.ai/download)",
                e
            )
        }

        // 프롬프트를 stdin으로 write 후 즉시 close (EOF 전송)
        try {
            process.outputStream.bufferedWriter().use { it.write(prompt) }
        } catch (e: Exception) {
            process.destroyForcibly()
            throw LlmException("Claude CLI stdin 전송 실패: ${e.message}", e)
        }

        return if (onChunk != null) {
            readStreaming(process, onChunk)
        } else {
            readBlocking(process)
        }
    }

    /**
     * 스트리밍 모드: stdout을 읽으면서 onChunk 콜백 호출.
     * watchdog 스레드로 TIMEOUT_SECONDS 후 강제 종료 보장.
     */
    private fun readStreaming(process: Process, onChunk: (String) -> Unit): String {
        val sb = StringBuilder()

        // stdout 읽기 스레드
        val readerThread = Thread {
            try {
                val reader = process.inputStream.bufferedReader()
                val buffer = CharArray(512)
                var charsRead: Int
                while (reader.read(buffer).also { charsRead = it } != -1) {
                    synchronized(sb) { sb.append(buffer, 0, charsRead) }
                    onChunk(synchronized(sb) { sb.toString() })
                }
            } catch (_: Exception) { }
        }
        readerThread.isDaemon = true
        readerThread.start()

        val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        readerThread.join(2_000)

        if (!finished) {
            process.destroyForcibly()
            val partial = synchronized(sb) { sb.toString().trim() }
            if (partial.isNotBlank()) {
                onChunk("$partial\n\n⚠️ 응답 시간 초과로 부분 결과만 표시됩니다.")
                return partial
            }
            throw LlmException(
                "Claude CLI 응답 시간 초과 (${TIMEOUT_SECONDS}초)\n" +
                "› 네트워크 상태나 claude 로그인 여부를 확인해주세요."
            )
        }

        val output = synchronized(sb) { sb.toString().trim() }
        checkNotLoggedIn(output)

        val exitCode = runCatching { process.exitValue() }.getOrDefault(-1)
        if (exitCode != 0 && output.isBlank()) {
            throw LlmException("Claude CLI 오류 (exit $exitCode). Settings에서 버전 확인 버튼으로 상태를 확인해주세요.")
        }

        return output.ifBlank { throw LlmException("Claude CLI 응답이 비어있습니다.") }
    }

    /** 블로킹 모드: 전체 응답을 한 번에 읽음 */
    private fun readBlocking(process: Process): String {
        val sb = StringBuilder()

        val readerThread = Thread {
            try {
                sb.append(process.inputStream.bufferedReader().readText())
            } catch (_: Exception) { }
        }
        readerThread.isDaemon = true
        readerThread.start()

        val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        readerThread.join(2_000)

        if (!finished) {
            process.destroyForcibly()
            throw LlmException(
                "Claude CLI 응답 시간 초과 (${TIMEOUT_SECONDS}초)\n" +
                "› 네트워크 상태나 claude 로그인 여부를 확인해주세요."
            )
        }

        val output = sb.toString().trim()
        val exitCode = runCatching { process.exitValue() }.getOrDefault(-1)
        if (exitCode != 0) {
            throw LlmException("Claude CLI 오류 (exit $exitCode):\n${output.take(300)}")
        }

        return output.ifBlank { throw LlmException("Claude CLI 응답이 비어있습니다.") }
    }

    private fun checkNotLoggedIn(output: String) {
        if (output.contains("Not logged in", ignoreCase = true) ||
            output.contains("Please run /login", ignoreCase = true) ||
            output.contains("not authenticated", ignoreCase = true)
        ) {
            throw LlmException(
                "Claude CLI 로그인이 필요합니다.\n" +
                "› Settings → Tools → Niku Agent에서 '터미널에서 로그인' 버튼을 클릭하거나\n" +
                "› 터미널에서 `claude /login`을 실행해주세요."
            )
        }
    }

    companion object {
        private const val TIMEOUT_SECONDS = 120L

        private val CANDIDATE_PATHS = listOf(
            "/usr/local/bin/claude",
            "/opt/homebrew/bin/claude",
            "${System.getProperty("user.home")}/.local/bin/claude",
            "${System.getProperty("user.home")}/.npm/bin/claude",
            "${System.getProperty("user.home")}/.nvm/bin/claude",
        )

        fun findBinary(): String? {
            for (path in CANDIDATE_PATHS) {
                if (File(path).canExecute()) return path
            }
            return try {
                val proc = ProcessBuilder("which", "claude")
                    .redirectErrorStream(true)
                    .start()
                proc.waitFor(5, TimeUnit.SECONDS)
                val result = proc.inputStream.bufferedReader().readText().trim()
                result.takeIf { it.isNotBlank() && File(it).canExecute() }
            } catch (_: Exception) {
                null
            }
        }

        fun checkVersion(binaryPath: String): String? {
            return try {
                val proc = ProcessBuilder(binaryPath, "--version")
                    .redirectErrorStream(true)
                    .start()
                proc.waitFor(5, TimeUnit.SECONDS)
                proc.inputStream.bufferedReader().readText().trim().takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }
    }
}
