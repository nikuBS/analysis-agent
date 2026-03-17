package com.nikuagent.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRadioButton

/**
 * Settings → Tools → Niku Agent 설정 화면.
 *
 * 제공 항목:
 * - LLM Provider 선택 (OpenAI / Anthropic)
 * - OpenAI API Key + 모델
 * - Anthropic API Key + 모델
 */
class NikuSettingsConfigurable : Configurable {

    private var openAiRadio: JRadioButton? = null
    private var anthropicRadio: JRadioButton? = null
    private var openAiApiKeyField: JBPasswordField? = null
    private var openAiModelField: JBTextField? = null
    private var anthropicApiKeyField: JBPasswordField? = null
    private var anthropicModelField: JBTextField? = null
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Niku Agent"

    override fun createComponent(): JComponent {
        openAiRadio = JRadioButton("OpenAI")
        anthropicRadio = JRadioButton("Anthropic (Claude)")
        ButtonGroup().apply {
            add(openAiRadio)
            add(anthropicRadio)
        }

        openAiApiKeyField = JBPasswordField().apply { columns = 40 }
        openAiModelField = JBTextField().apply { columns = 20 }
        anthropicApiKeyField = JBPasswordField().apply { columns = 40 }
        anthropicModelField = JBTextField().apply { columns = 20 }

        panel = FormBuilder.createFormBuilder()
            .addSeparator()
            .addComponent(JBLabel("LLM Provider"))
            .addComponent(openAiRadio!!)
            .addComponent(anthropicRadio!!)
            .addSeparator()
            .addComponent(JBLabel("OpenAI 설정"))
            .addLabeledComponent(JBLabel("API Key:"), openAiApiKeyField!!, true)
            .addLabeledComponent(JBLabel("Model:"), openAiModelField!!, true)
            .addSeparator()
            .addComponent(JBLabel("Anthropic (Claude) 설정"))
            .addLabeledComponent(JBLabel("API Key:"), anthropicApiKeyField!!, true)
            .addLabeledComponent(JBLabel("Model:"), anthropicModelField!!, true)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        val s = NikuSettings.getInstance().state
        return selectedProvider() != (s.provider ?: "openai") ||
               String(openAiApiKeyField!!.password) != (s.openAiApiKey ?: "") ||
               openAiModelField!!.text != (s.openAiModel ?: "gpt-4o") ||
               String(anthropicApiKeyField!!.password) != (s.anthropicApiKey ?: "") ||
               anthropicModelField!!.text != (s.anthropicModel ?: "claude-sonnet-4-6")
    }

    override fun apply() {
        val s = NikuSettings.getInstance().state
        s.provider = selectedProvider()
        s.openAiApiKey = String(openAiApiKeyField!!.password).ifBlank { null }
        s.openAiModel = openAiModelField!!.text.ifBlank { "gpt-4o" }
        s.anthropicApiKey = String(anthropicApiKeyField!!.password).ifBlank { null }
        s.anthropicModel = anthropicModelField!!.text.ifBlank { "claude-sonnet-4-6" }
    }

    override fun reset() {
        val s = NikuSettings.getInstance().state
        if ((s.provider ?: "openai") == "anthropic") {
            anthropicRadio!!.isSelected = true
        } else {
            openAiRadio!!.isSelected = true
        }
        openAiApiKeyField!!.text = s.openAiApiKey ?: ""
        openAiModelField!!.text = s.openAiModel ?: "gpt-4o"
        anthropicApiKeyField!!.text = s.anthropicApiKey ?: ""
        anthropicModelField!!.text = s.anthropicModel ?: "claude-sonnet-4-6"
    }

    override fun disposeUIResources() {
        panel = null
        openAiRadio = null
        anthropicRadio = null
        openAiApiKeyField = null
        openAiModelField = null
        anthropicApiKeyField = null
        anthropicModelField = null
    }

    private fun selectedProvider(): String =
        if (anthropicRadio?.isSelected == true) "anthropic" else "openai"
}
