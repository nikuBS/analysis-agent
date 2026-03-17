package com.nikuagent.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.nikuagent.context.FileContext
import com.nikuagent.formatter.ResultFormatter
import com.nikuagent.prompt.PromptBuilder

/**
 * 플러그인의 핵심 서비스.
 *
 * 분석 파이프라인을 조율한다:
 * [FileContext] → [PromptBuilder] → [LlmClient] → [ResultFormatter] → HTML 결과
 *
 * IntelliJ Application Service로 등록되어 싱글턴으로 관리된다.
 *
 * TODO: 실제 API 연동 시 [llmClient]를 [OpenAiLlmClient]로 교체
 */
@Service(Service.Level.APP)
class NikuAgentService {

    private val log = logger<NikuAgentService>()

    // TODO: 실제 API 연동 시 이 부분을 교체한다.
    //   옵션 A: 직접 교체 → MockLlmClient() → OpenAiLlmClient(apiKey)
    //   옵션 B: PluginSettings에서 apiKey를 읽어 주입
    //   옵션 C: 의존성 주입 프레임워크 도입
    private val llmClient: LlmClient = MockLlmClient()

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

            val rawResponse = llmClient.complete(prompt)
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
