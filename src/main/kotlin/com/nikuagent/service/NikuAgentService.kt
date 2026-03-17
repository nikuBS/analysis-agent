package com.nikuagent.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.nikuagent.context.FileContext
import com.nikuagent.formatter.ResultFormatter
import com.nikuagent.prompt.PromptBuilder
import com.nikuagent.settings.NikuSettings

/**
 * 플러그인의 핵심 서비스.
 *
 * 분석 파이프라인을 조율한다:
 * [FileContext] → [PromptBuilder] → [CliLlmClient] → [ResultFormatter] → HTML 결과
 *
 * Claude CLI(`claude --print`)를 사용한다.
 * CLI가 미설치/미로그인 상태이면 [MockLlmClient]로 폴백한다.
 */
@Service(Service.Level.APP)
class NikuAgentService {

    private val log = logger<NikuAgentService>()

    /**
     * 매 호출 시 Settings에서 최신 설정을 읽어 클라이언트를 결정한다.
     *
     * 우선순위:
     * 1. Settings에 지정된 cliBinaryPath
     * 2. 자동 탐색 (known paths + `which claude`)
     * 3. 탐색 실패 시 MockLlmClient
     */
    private fun resolveLlmClient(): LlmClient {
        val s = NikuSettings.getInstance().state

        val binaryPath = s.cliBinaryPath?.takeIf { it.isNotBlank() }
            ?: CliLlmClient.findBinary()

        return if (binaryPath != null) {
            log.info("Niku Agent: Claude CLI 사용 — $binaryPath")
            CliLlmClient(
                binaryPath = binaryPath,
                model = s.cliModel?.takeIf { it.isNotBlank() },
            )
        } else {
            log.warn("Niku Agent: claude CLI를 찾을 수 없습니다 — Mock 응답을 사용합니다.")
            MockLlmClient()
        }
    }

    /**
     * 파일 컨텍스트를 분석하고 HTML 형식의 결과를 반환한다.
     *
     * 긴 작업이므로 호출 측에서 반드시 백그라운드 스레드에서 실행해야 한다.
     *
     * @param context       분석 대상 파일 컨텍스트
     * @param customPrompt  커스텀 프롬프트 (null이면 표준 7섹션 분석)
     * @return              UI에 표시할 HTML 문자열
     */
    fun analyze(
        context: FileContext,
        customPrompt: String? = null,
        onChunk: ((accumulated: String) -> Unit)? = null,
    ): String {
        log.info("Niku Agent: analyzing ${context.fileName} (${context.language})" +
                if (customPrompt != null) " [custom prompt]" else "")

        return try {
            val prompt = if (customPrompt != null) {
                PromptBuilder.buildCustom(context, customPrompt)
            } else {
                PromptBuilder.build(context)
            }
            log.debug("Niku Agent: prompt built, length=${prompt.length}")

            val rawResponse = resolveLlmClient().complete(prompt, onChunk)
            log.debug("Niku Agent: response received, length=${rawResponse.length}")

            ResultFormatter.format(rawResponse, context)
        } catch (e: LlmException) {
            log.warn("Niku Agent: LLM call failed", e)
            buildErrorHtml("분석 실패: ${e.message}")
        } catch (e: Exception) {
            log.error("Niku Agent: unexpected error", e)
            buildErrorHtml("예상치 못한 오류가 발생했습니다: ${e.message}")
        }
    }

    private fun buildErrorHtml(message: String): String = """
        <html><body style="font-family:Arial;color:#F38BA8;padding:12px;">
        <b>❌ 분석 실패</b><br/><br/>$message
        </body></html>
    """.trimIndent()
}
