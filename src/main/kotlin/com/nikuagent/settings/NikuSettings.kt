package com.nikuagent.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/**
 * API 키 등 플러그인 설정을 영속적으로 저장한다.
 *
 * 저장 위치: ~/Library/Application Support/JetBrains/<IDE>/options/NikuAgent.xml
 * (IDE 재시작 후에도 유지됨)
 *
 * 사용법: NikuSettings.getInstance().state.openAiApiKey
 */
@State(
    name = "NikuAgentSettings",
    storages = [Storage("NikuAgent.xml")],
)
@Service(Service.Level.APP)
class NikuSettings : SimplePersistentStateComponent<NikuSettings.State>(State()) {

    class State : BaseState() {
        // LLM 제공사 선택: "openai" 또는 "anthropic"
        var provider: String? by string("openai")

        // OpenAI 설정
        var openAiApiKey: String? by string()
        var openAiModel: String? by string("gpt-4o")

        // Anthropic (Claude) 설정
        var anthropicApiKey: String? by string()
        var anthropicModel: String? by string("claude-sonnet-4-6")

        var maxTokens: Int by property(2048)

        // 하위 호환: 기존 model 필드 → openAiModel로 이전
        @Deprecated("openAiModel 사용")
        var model: String? by string()
    }

    companion object {
        fun getInstance(): NikuSettings = service()
    }
}
