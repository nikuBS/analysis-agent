package com.nikuagent.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRadioButton

/**
 * Settings → Tools → Niku Agent 설정 화면.
 *
 * 제공 항목:
 * - LLM Provider 선택 (OpenAI / Anthropic)
 * - 선택된 Provider 패널만 표시 (동적 show/hide)
 * - 각 Provider별 API Key + 모델 드롭다운 (4개)
 */
class NikuSettingsConfigurable : Configurable {

    private var openAiRadio: JRadioButton? = null
    private var anthropicRadio: JRadioButton? = null
    private var openAiApiKeyField: JBPasswordField? = null
    private var openAiModelCombo: JComboBox<String>? = null
    private var anthropicApiKeyField: JBPasswordField? = null
    private var anthropicModelCombo: JComboBox<String>? = null

    private var openAiPanel: JPanel? = null
    private var anthropicPanel: JPanel? = null
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
        openAiModelCombo = JComboBox(OPENAI_MODELS)

        anthropicApiKeyField = JBPasswordField().apply { columns = 40 }
        anthropicModelCombo = JComboBox(ANTHROPIC_MODELS)

        openAiPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("API Key:"), openAiApiKeyField!!, true)
            .addLabeledComponent(JBLabel("Model:"), openAiModelCombo!!, true)
            .panel
            .also { it.border = BorderFactory.createTitledBorder("OpenAI 설정") }

        anthropicPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("API Key:"), anthropicApiKeyField!!, true)
            .addLabeledComponent(JBLabel("Model:"), anthropicModelCombo!!, true)
            .panel
            .also { it.border = BorderFactory.createTitledBorder("Anthropic (Claude) 설정") }

        val radioPanel = FormBuilder.createFormBuilder()
            .addComponent(JBLabel("LLM Provider"))
            .addComponent(openAiRadio!!)
            .addComponent(anthropicRadio!!)
            .panel

        panel = JPanel(BorderLayout()).apply {
            val top = JPanel(BorderLayout())
            top.add(radioPanel, BorderLayout.NORTH)
            top.add(openAiPanel!!, BorderLayout.CENTER)
            top.add(anthropicPanel!!, BorderLayout.SOUTH)
            add(top, BorderLayout.NORTH)
        }

        openAiRadio!!.addActionListener { updatePanelVisibility() }
        anthropicRadio!!.addActionListener { updatePanelVisibility() }

        reset()
        return panel!!
    }

    private fun updatePanelVisibility() {
        val isAnthropic = anthropicRadio?.isSelected == true
        openAiPanel?.isVisible = !isAnthropic
        anthropicPanel?.isVisible = isAnthropic
        panel?.revalidate()
        panel?.repaint()
    }

    override fun isModified(): Boolean {
        val s = NikuSettings.getInstance().state
        return selectedProvider() != (s.provider ?: "openai") ||
               String(openAiApiKeyField!!.password) != (s.openAiApiKey ?: "") ||
               openAiModelCombo!!.selectedItem as String != (s.openAiModel ?: OPENAI_MODELS[0]) ||
               String(anthropicApiKeyField!!.password) != (s.anthropicApiKey ?: "") ||
               anthropicModelCombo!!.selectedItem as String != (s.anthropicModel ?: ANTHROPIC_MODELS[0])
    }

    override fun apply() {
        val s = NikuSettings.getInstance().state
        s.provider = selectedProvider()
        s.openAiApiKey = String(openAiApiKeyField!!.password).ifBlank { null }
        s.openAiModel = openAiModelCombo!!.selectedItem as String
        s.anthropicApiKey = String(anthropicApiKeyField!!.password).ifBlank { null }
        s.anthropicModel = anthropicModelCombo!!.selectedItem as String
    }

    override fun reset() {
        val s = NikuSettings.getInstance().state
        if ((s.provider ?: "openai") == "anthropic") {
            anthropicRadio!!.isSelected = true
        } else {
            openAiRadio!!.isSelected = true
        }

        openAiApiKeyField!!.text = s.openAiApiKey ?: ""
        val savedOpenAiModel = s.openAiModel ?: OPENAI_MODELS[0]
        openAiModelCombo!!.selectedItem =
            if (OPENAI_MODELS.contains(savedOpenAiModel)) savedOpenAiModel else OPENAI_MODELS[0]

        anthropicApiKeyField!!.text = s.anthropicApiKey ?: ""
        val savedAnthropicModel = s.anthropicModel ?: ANTHROPIC_MODELS[0]
        anthropicModelCombo!!.selectedItem =
            if (ANTHROPIC_MODELS.contains(savedAnthropicModel)) savedAnthropicModel else ANTHROPIC_MODELS[0]

        updatePanelVisibility()
    }

    override fun disposeUIResources() {
        panel = null
        openAiRadio = null
        anthropicRadio = null
        openAiApiKeyField = null
        openAiModelCombo = null
        anthropicApiKeyField = null
        anthropicModelCombo = null
        openAiPanel = null
        anthropicPanel = null
    }

    private fun selectedProvider(): String =
        if (anthropicRadio?.isSelected == true) "anthropic" else "openai"

    companion object {
        private val OPENAI_MODELS = arrayOf(
            "gpt-4o",
            "gpt-4o-mini",
            "gpt-4-turbo",
            "gpt-3.5-turbo",
        )

        private val ANTHROPIC_MODELS = arrayOf(
            "claude-sonnet-4-6",
            "claude-opus-4-5-20251101",
            "claude-haiku-4-5-20251001",
            "claude-3-5-sonnet-20241022",
        )
    }
}
