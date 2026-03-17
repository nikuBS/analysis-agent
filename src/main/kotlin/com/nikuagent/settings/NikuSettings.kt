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
        // BaseState.string()은 String? 위임 — 빈 문자열 대신 null로 초기화
        var openAiApiKey: String? by string()
        var model: String? by string("gpt-4o")
        var maxTokens: Int by property(2048)
    }

    companion object {
        fun getInstance(): NikuSettings = service()
    }
}
