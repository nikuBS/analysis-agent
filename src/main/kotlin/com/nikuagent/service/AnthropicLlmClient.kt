package com.nikuagent.service

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Anthropic Claude Messages API 클라이언트.
 *
 * 사용 모델: claude-sonnet-4-6 (기본값, Settings에서 변경 가능)
 * API 문서: https://docs.anthropic.com/en/api/messages
 */
class AnthropicLlmClient(
    private val apiKey: String,
    private val model: String = "claude-sonnet-4-6",
) : LlmClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override fun complete(prompt: String): String {
        if (apiKey.isBlank()) {
            throw LlmException("API 키가 설정되지 않았습니다. Settings → Tools → Niku Agent에서 Anthropic API Key를 입력해주세요.")
        }

        val requestBody = buildRequestBody(prompt)
        val request = Request.Builder()
            .url(API_URL)
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("Content-Type", "application/json")
            .post(requestBody.toRequestBody(jsonMediaType))
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            throw LlmException("네트워크 오류: ${e.message}", e)
        }

        return response.use { res ->
            val body = res.body?.string() ?: throw LlmException("응답 본문이 비어있습니다.")
            if (!res.isSuccessful) {
                val error = runCatching { gson.fromJson(body, ErrorResponse::class.java) }.getOrNull()
                throw LlmException("API 오류 (${res.code}): ${error?.error?.message ?: body}")
            }
            parseResponse(body)
        }
    }

    private fun buildRequestBody(prompt: String): String {
        val payload = MessagesRequest(
            model = model,
            maxTokens = 2048,
            system = SYSTEM_PROMPT,
            messages = listOf(Message(role = "user", content = prompt)),
        )
        return gson.toJson(payload)
    }

    private fun parseResponse(json: String): String {
        val response = gson.fromJson(json, MessagesResponse::class.java)
        return response.content
            .firstOrNull { it.type == "text" }
            ?.text
            ?: throw LlmException("응답에서 텍스트 컨텐츠를 추출할 수 없습니다.")
    }

    // --- Request/Response 데이터 클래스 ---

    private data class MessagesRequest(
        val model: String,
        @SerializedName("max_tokens") val maxTokens: Int,
        val system: String,
        val messages: List<Message>,
    )

    private data class Message(
        val role: String,
        val content: String,
    )

    private data class MessagesResponse(
        val content: List<ContentBlock>,
    )

    private data class ContentBlock(
        val type: String,
        val text: String?,
    )

    private data class ErrorResponse(
        val error: ApiError?,
    )

    private data class ApiError(
        val message: String?,
    )

    companion object {
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        private const val ANTHROPIC_VERSION = "2023-06-01"

        private const val SYSTEM_PROMPT = """
당신은 프론트엔드 시니어 개발자이자 코드 분석 전문가입니다.
React, TypeScript, JavaScript 코드를 분석하여 업무 프로세스와 동작 흐름을 명확하게 설명합니다.
항상 한국어로 답변하며, 지정된 Markdown 형식을 정확히 따릅니다.
코드를 수정하거나 리팩토링하는 제안은 하지 않습니다. 분석만 수행합니다.
"""
    }
}
