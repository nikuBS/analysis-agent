package com.nikuagent.service

/**
 * LLM 호출 인터페이스.
 *
 * 구현체를 교체하는 것만으로 다양한 LLM 서비스에 대응할 수 있다.
 * - [MockLlmClient]: MVP 개발/테스트용
 * - OpenAiLlmClient: 실제 OpenAI API 연동 (TODO)
 * - AnthropicLlmClient: Claude API 연동 (TODO)
 */
interface LlmClient {
    /**
     * 프롬프트를 전송하고 응답 텍스트를 반환한다.
     *
     * @param prompt  전송할 프롬프트 문자열
     * @return        LLM 응답 텍스트
     * @throws LlmException API 호출 실패 시
     */
    fun complete(prompt: String): String
}

/**
 * LLM 호출 관련 예외.
 */
class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause)
