package com.nikuagent.ui

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.nikuagent.context.FileContext
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.SwingUtilities

/**
 * Niku Agent Tool Window의 메인 패널.
 *
 * 상태 전환:
 * - 초기:    안내 메시지 (showWelcome)
 * - 옵션:    분석 유형 선택 + 커스텀 프롬프트 입력 (showOptions)
 * - 로딩:    분석 진행 중 (showLoading)
 * - 결과:    HTML 분석 결과 (showResult)
 */
class NikuToolWindowPanel : JPanel(BorderLayout()) {

    private val resultPane = JEditorPane().apply {
        contentType = "text/html"
        isEditable = false
        background = null
        cursor = Cursor.getDefaultCursor()
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        font = Font("Arial", Font.PLAIN, 13)
    }

    private val scrollPane = JBScrollPane(resultPane).apply {
        border = null
    }

    init {
        add(scrollPane, BorderLayout.CENTER)
        showWelcome()
    }

    /** 초기 안내 화면 */
    fun showWelcome() {
        swapContent(scrollPane)
        setHtml(
            """
            <html>
            <body style="font-family:-apple-system,Arial,sans-serif;
                         padding:24px;color:#CDD6F4;background:#1E1E2E;text-align:center;">
              <br/><br/>
              <h2 style="color:#89B4FA;">🔍 Niku Agent</h2>
              <p style="color:#A6ADC8;">프론트엔드 코드 흐름 분석기</p>
              <br/>
              <p>분석할 파일을 열고<br/>
                 <b style="color:#CBA6F7;">우클릭 → Analyze with Niku Agent</b><br/>
                 또는 <b style="color:#CBA6F7;">Ctrl+Alt+N</b>을 누르세요.</p>
              <br/>
              <p style="color:#6C7086;font-size:11px;">
                React · TypeScript · JavaScript 파일을 지원합니다.
              </p>
            </body>
            </html>
            """.trimIndent()
        )
    }

    /**
     * 분석 옵션 선택 화면을 표시한다.
     *
     * @param context    수집된 파일 컨텍스트 (파일명, 선택 정보 표시용)
     * @param onAnalyze  사용자가 "분석 시작"을 클릭했을 때 호출되는 콜백.
     *                   표준 분석이면 null, 커스텀이면 입력된 프롬프트 문자열을 전달한다.
     */
    fun showOptions(context: FileContext, onAnalyze: (customPrompt: String?) -> Unit) {
        SwingUtilities.invokeLater {
            swapContent(buildOptionsPanel(context, onAnalyze))
        }
    }

    /** 로딩 상태 화면 (스트리밍 시작 전 초기 표시용) */
    fun showLoading() {
        swapContent(scrollPane)
        setHtml(
            """
            <html>
            <body style="font-family:Arial,sans-serif;padding:24px;
                         color:#CDD6F4;background:#1E1E2E;text-align:center;">
              <br/><br/>
              <p style="font-size:32px;">⏳</p>
              <p style="color:#89B4FA;font-size:15px;">Claude에 요청 중...</p>
              <p style="color:#6C7086;font-size:12px;">응답이 시작되면 실시간으로 표시됩니다.</p>
            </body>
            </html>
            """.trimIndent()
        )
    }

    /**
     * CLI 응답을 실시간으로 표시한다 (스트리밍).
     *
     * 누적된 원문 텍스트를 pre 태그로 표시하며,
     * 분석 완료 후 [showResult]로 포맷된 HTML로 교체된다.
     *
     * @param accumulated  지금까지 수신된 전체 텍스트
     */
    fun showStreaming(accumulated: String) {
        swapContent(scrollPane)
        SwingUtilities.invokeLater {
            val escaped = accumulated
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            resultPane.text = """
                <html>
                <body style="font-family:Arial,monospace;padding:12px;background:#1E1E2E;color:#CDD6F4;">
                <pre style="white-space:pre-wrap;font-size:12px;margin:0;line-height:1.5;">$escaped<span style="color:#89B4FA;">▌</span></pre>
                </body>
                </html>
            """.trimIndent()
            // 스트리밍 중 스크롤을 항상 하단으로 유지
            resultPane.caretPosition = maxOf(0, resultPane.document.length - 1)
        }
    }

    /** 분석 결과 HTML 표시 (스트리밍 완료 후 포맷된 결과로 교체) */
    fun showResult(html: String) {
        swapContent(scrollPane)
        setHtml(html)
        SwingUtilities.invokeLater {
            resultPane.caretPosition = 0
        }
    }

    // --- private helpers ---

    private fun swapContent(newContent: java.awt.Component) {
        SwingUtilities.invokeLater {
            removeAll()
            add(newContent, BorderLayout.CENTER)
            revalidate()
            repaint()
        }
    }

    private fun setHtml(html: String) {
        SwingUtilities.invokeLater {
            resultPane.text = html
        }
    }

    private fun buildOptionsPanel(
        context: FileContext,
        onAnalyze: (String?) -> Unit,
    ): JPanel {
        val root = JPanel()
        root.layout = BoxLayout(root, BoxLayout.Y_AXIS)
        root.border = BorderFactory.createEmptyBorder(12, 12, 12, 12)

        // --- 분석 대상 정보 ---
        val contextDesc = buildContextDescription(context)
        val contextPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("분석 대상")
            add(JBLabel("<html>$contextDesc</html>"), BorderLayout.CENTER)
        }
        root.add(contextPanel)
        root.add(Box.createVerticalStrut(10))

        // --- 분석 유형 선택 ---
        val standardRadio = JRadioButton("표준 분석  (코드 구조 · 흐름 · API 7개 섹션)").apply { isSelected = true }
        val customRadio   = JRadioButton("커스텀 프롬프트  (직접 질문 입력)")
        ButtonGroup().apply { add(standardRadio); add(customRadio) }

        val typePanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createTitledBorder("분석 유형")
            add(standardRadio)
            add(Box.createVerticalStrut(4))
            add(customRadio)
        }
        root.add(typePanel)
        root.add(Box.createVerticalStrut(8))

        // --- 커스텀 프롬프트 입력란 (초기 숨김) ---
        val promptArea = JBTextArea(5, 40).apply {
            lineWrap = true
            wrapStyleWord = true
            emptyText.text = "예: 이 컴포넌트의 렌더링 조건을 정리해줘"
        }
        val promptScroll = JBScrollPane(promptArea).apply {
            border = BorderFactory.createTitledBorder("질문 / 요청 내용")
            isVisible = false
        }
        root.add(promptScroll)

        customRadio.addActionListener {
            promptScroll.isVisible = true
            root.revalidate()
            root.repaint()
            promptArea.requestFocusInWindow()
        }
        standardRadio.addActionListener {
            promptScroll.isVisible = false
            root.revalidate()
            root.repaint()
        }

        root.add(Box.createVerticalStrut(10))

        // --- 분석 시작 버튼 ---
        val analyzeBtn = JButton("▶  분석 시작").apply {
            font = Font(font.name, Font.BOLD, 13)
            addActionListener {
                val customPrompt = if (customRadio.isSelected) {
                    promptArea.text.trim().takeIf { it.isNotBlank() }
                } else {
                    null
                }
                onAnalyze(customPrompt)
            }
        }
        val btnPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { add(analyzeBtn) }
        root.add(btnPanel)

        return root
    }

    private fun buildContextDescription(context: FileContext): String {
        val selectionInfo = when {
            context.focusFunctionName != null ->
                "<br/>🎯 집중 분석: <b>${context.focusFunctionName}</b>"
            context.selection != null ->
                "<br/>✂️ 선택 코드: ${context.selection.lines().size}줄"
            else ->
                "<br/>📄 전체 파일 분석"
        }
        return "📁 <b>${context.fileName}</b>  <span style='color:gray;'>${context.language}</span>$selectionInfo"
    }
}
