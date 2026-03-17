package com.nikuagent.service

/**
 * LLM 호출 인터페이스.
 *
 * 구현체를 교체하는 것만으로 다양한 LLM 서비스에 대응할 수 있다.
 * - [MockLlmClient]: MVP 개발/테스트용
 * - [CliLlmClient]: Claude CLI 방식 (현재 메인)
 * - OpenAiLlmClient / AnthropicLlmClient: API Key 방식 (레거시)
 */
interface LlmClient {
    /**
     * 프롬프트를 전송하고 응답 텍스트를 반환한다.
     *
     * @param prompt   전송할 프롬프트 문자열
     * @param onChunk  스트리밍 중 누적 텍스트를 전달하는 콜백. null이면 스트리밍 없이 완료 후 반환.
     * @return         LLM 응답 텍스트
     * @throws LlmException 호출 실패 시
     */
    fun complete(prompt: String, onChunk: ((accumulated: String) -> Unit)? = null): String
}

/**
 * LLM 호출 관련 예외.
 */
class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause)
