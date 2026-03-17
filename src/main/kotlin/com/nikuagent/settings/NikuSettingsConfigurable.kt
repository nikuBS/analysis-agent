package com.nikuagent.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings → Tools → Niku Agent 설정 화면.
 *
 * 제공 항목:
 * - OpenAI API Key (마스킹 처리)
 * - 사용 모델 (기본: gpt-4o)
 */
class NikuSettingsConfigurable : Configurable {

    private var apiKeyField: JBPasswordField? = null
    private var modelField: JBTextField? = null
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Niku Agent"

    override fun createComponent(): JComponent {
        apiKeyField = JBPasswordField().apply {
            columns = 40
        }
        modelField = JBTextField().apply {
            columns = 20
        }

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("OpenAI API Key:"), apiKeyField!!, true)
            .addLabeledComponent(JBLabel("Model:"), modelField!!, true)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        reset() // 저장된 값으로 초기화
        return panel!!
    }

    override fun isModified(): Boolean {
        val state = NikuSettings.getInstance().state
        return String(apiKeyField!!.password) != (state.openAiApiKey ?: "") ||
               modelField!!.text != (state.model ?: "gpt-4o")
    }

    override fun apply() {
        val state = NikuSettings.getInstance().state
        state.openAiApiKey = String(apiKeyField!!.password).ifBlank { null }
        state.model = modelField!!.text.ifBlank { "gpt-4o" }
    }

    override fun reset() {
        val state = NikuSettings.getInstance().state
        apiKeyField!!.text = state.openAiApiKey ?: ""
        modelField!!.text = state.model ?: "gpt-4o"
    }

    override fun disposeUIResources() {
        panel = null
        apiKeyField = null
        modelField = null
    }
}
