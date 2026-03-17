package com.nikuagent.service

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Claude Code CLI(`claude`)를 subprocess로 실행하는 LLM 클라이언트.
 *
 * API 키 없이 `claude --print "prompt"` 형태로 실행한다.
 * claude CLI가 로그인된 상태여야 동작한다.
 *
 * @param binaryPath  claude 실행 파일 경로 (예: /usr/local/bin/claude)
 * @param model       사용할 모델 (null이면 CLI 기본값 사용)
 */
class CliLlmClient(
    private val binaryPath: String,
    private val model: String? = null,
) : LlmClient {

    /**
     * @param onChunk  null이 아니면 응답을 청크 단위로 스트리밍한다.
     *                 파라미터로 지금까지 누적된 전체 텍스트를 전달한다.
     */
    override fun complete(prompt: String, onChunk: ((String) -> Unit)?): String {
        val cmd = buildList {
            add(binaryPath)
            add("--print")
            if (!model.isNullOrBlank()) {
                add("--model")
                add(model)
            }
            add(prompt)
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

        return if (onChunk != null) {
            readStreaming(process, onChunk)
        } else {
            readBlocking(process)
        }
    }

    /** 스트리밍: stdout을 청크 단위로 읽으며 onChunk 호출 */
    private fun readStreaming(process: Process, onChunk: (String) -> Unit): String {
        val sb = StringBuilder()
        val reader = process.inputStream.bufferedReader()
        val buffer = CharArray(256)
        var charsRead: Int

        while (reader.read(buffer).also { charsRead = it } != -1) {
            sb.append(buffer, 0, charsRead)
            onChunk(sb.toString())
        }

        val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw LlmException("Claude CLI 응답 시간 초과 (${TIMEOUT_SECONDS}초)")
        }

        val exitCode = process.exitValue()
        // exit 1은 CLI 경고일 수 있으므로, 응답이 있으면 성공으로 처리
        if (exitCode != 0 && sb.isBlank()) {
            throw LlmException("Claude CLI 오류 (exit $exitCode): 응답이 비어있습니다.")
        }

        return sb.toString().trim()
    }

    /** 블로킹: 전체 응답을 한 번에 읽음 */
    private fun readBlocking(process: Process): String {
        val output = process.inputStream.bufferedReader().readText()
        val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        if (!finished) {
            process.destroyForcibly()
            throw LlmException("Claude CLI 응답 시간 초과 (${TIMEOUT_SECONDS}초)")
        }

        val exitCode = process.exitValue()
        if (exitCode != 0) {
            throw LlmException("Claude CLI 오류 (exit $exitCode):\n$output")
        }

        return output.trim().ifBlank {
            throw LlmException("Claude CLI 응답이 비어있습니다.")
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
