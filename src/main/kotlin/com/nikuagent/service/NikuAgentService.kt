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
 * [FileContext] → [PromptBuilder] → [LlmClient] → [ResultFormatter] → HTML 결과
 *
 * API 키가 설정되어 있으면 [OpenAiLlmClient], 없으면 [MockLlmClient]를 사용한다.
 */
@Service(Service.Level.APP)
class NikuAgentService {

    private val log = logger<NikuAgentService>()

    /**
     * 매 호출 시 Settings에서 최신 API 키를 읽어 클라이언트를 결정한다.
     * Settings 화면에서 키를 저장하면 즉시 반영된다.
     */
    private fun resolveLlmClient(): LlmClient {
        val s = NikuSettings.getInstance().state
        return when (s.provider ?: "openai") {
            "anthropic" -> {
                if (!s.anthropicApiKey.isNullOrBlank()) {
                    AnthropicLlmClient(apiKey = s.anthropicApiKey!!, model = s.anthropicModel ?: "claude-sonnet-4-6")
                } else {
                    log.warn("Niku Agent: Anthropic API 키 미설정 — Mock 응답을 사용합니다.")
                    MockLlmClient()
                }
            }
            else -> { // "openai"
                if (!s.openAiApiKey.isNullOrBlank()) {
                    OpenAiLlmClient(apiKey = s.openAiApiKey!!, model = s.openAiModel ?: "gpt-4o")
                } else {
                    log.warn("Niku Agent: OpenAI API 키 미설정 — Mock 응답을 사용합니다.")
                    MockLlmClient()
                }
            }
        }
    }

    /**
     * 파일 컨텍스트를 분석하고 HTML 형식의 결과를 반환한다.
     *
     * 긴 작업이므로 호출 측에서 반드시 백그라운드 스레드에서 실행해야 한다.
     * (EDT 블로킹 방지)
     *
     * @param context  분석 대상 파일 컨텍스트
     * @return         UI에 표시할 HTML 문자열
     */
    fun analyze(context: FileContext): String {
        log.info("Niku Agent: analyzing ${context.fileName} (${context.language})")

        return try {
            val prompt = PromptBuilder.build(context)
            log.debug("Niku Agent: prompt built, length=${prompt.length}")

            val rawResponse = resolveLlmClient().complete(prompt)
            log.debug("Niku Agent: LLM response received, length=${rawResponse.length}")

            ResultFormatter.format(rawResponse, context)
        } catch (e: LlmException) {
            log.warn("Niku Agent: LLM call failed", e)
            buildErrorHtml("LLM 호출 실패: ${e.message}")
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
