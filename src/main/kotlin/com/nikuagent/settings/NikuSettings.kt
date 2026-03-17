package com.nikuagent.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/**
 * 플러그인 설정을 영속적으로 저장한다.
 *
 * 저장 위치: ~/Library/Application Support/JetBrains/<IDE>/options/NikuAgent.xml
 *
 * Claude CLI 방식: API 키 없이 `claude --print` 명령을 사용한다.
 * claude CLI가 로그인된 상태여야 동작한다.
 */
@State(
    name = "NikuAgentSettings",
    storages = [Storage("NikuAgent.xml")],
)
@Service(Service.Level.APP)
class NikuSettings : SimplePersistentStateComponent<NikuSettings.State>(State()) {

    class State : BaseState() {
        // Claude CLI 실행 파일 경로 (비어있으면 자동 탐색)
        var cliBinaryPath: String? by string()

        // 사용할 모델 (비어있으면 CLI 기본값 사용)
        var cliModel: String? by string()

        // --- 하위 호환: API Key 방식 설정 (레거시, 미사용) ---
        // API Key 방식으로 되돌릴 때를 위해 필드는 유지한다.
        var provider: String? by string()
        var openAiApiKey: String? by string()
        var openAiModel: String? by string()
        var anthropicApiKey: String? by string()
        var anthropicModel: String? by string()
    }

    companion object {
        fun getInstance(): NikuSettings = service()
    }
}
