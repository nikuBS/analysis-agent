package com.nikuagent.ui

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.nikuagent.context.FileContext
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JTabbedPane
import javax.swing.SwingUtilities

/**
 * Niku Agent Tool Window 메인 패널.
 *
 * 구조:
 *  - CENTER: JTabbedPane (탭 히스토리)
 *    - 탭 0: 🏠 홈 (닫기 불가)
 *    - 탭 1~N: 분석 결과 (닫기 가능)
 *
 * 상태 전환 (각 탭 독립):
 *  options → loading → streaming → result / loginRequired
 */
class NikuToolWindowPanel : JPanel(BorderLayout()) {

    private val tabbedPane = JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT)

    /** 현재 진행 중인 분석 탭 인덱스 */
    private var activeTabIndex: Int = 0

    init {
        border = null
        add(tabbedPane, BorderLayout.CENTER)
        addWelcomeTab()
    }

    // ── Public API (AnalyzeCurrentFileAction 과 호환) ──────────────

    /** 새 분석 탭을 생성하고 옵션 패널을 표시한다. */
    fun showOptions(context: FileContext, onAnalyze: (customPrompt: String?) -> Unit) {
        SwingUtilities.invokeLater {
            val idx = addAnalysisTab(context)
            activeTabIndex = idx
            getTabPanel(idx).showOptions(context, onAnalyze)
        }
    }

    fun showLoading() = invokeOnActiveTab { it.showLoading() }

    fun showStreaming(accumulated: String) = invokeOnActiveTab { it.showStreaming(accumulated) }

    fun showResult(html: String) {
        invokeOnActiveTab { it.showResult(html) }
        // 탭 제목에서 ⏳ 제거
        SwingUtilities.invokeLater { refreshTabTitle(activeTabIndex) }
    }

    fun showLoginRequired(onRetry: (() -> Unit)? = null) =
        invokeOnActiveTab { it.showLoginRequired(onRetry) }

    // ── 탭 관리 ────────────────────────────────────────────────────

    private fun addWelcomeTab() {
        val panel = AnalysisTabPanel()
        panel.showWelcome()
        tabbedPane.addTab("🏠 홈", panel)
        // 홈 탭은 닫기 버튼 없음
    }

    private fun addAnalysisTab(context: FileContext): Int {
        val panel = AnalysisTabPanel()
        val title = context.fileName
        val idx = tabbedPane.tabCount
        tabbedPane.addTab(title, panel)
        tabbedPane.setTabComponentAt(idx, makeCloseableHeader("⏳ $title", idx))
        tabbedPane.selectedIndex = idx
        return idx
    }

    private fun refreshTabTitle(idx: Int) {
        if (idx <= 0 || idx >= tabbedPane.tabCount) return
        val comp = tabbedPane.getTabComponentAt(idx) as? JPanel ?: return
        val label = comp.components.filterIsInstance<JLabel>().firstOrNull() ?: return
        // ⏳ → 📄 로 교체
        label.text = label.text.replace("⏳ ", "📄 ")
    }

    private fun getTabPanel(index: Int): AnalysisTabPanel =
        tabbedPane.getComponentAt(index) as AnalysisTabPanel

    private fun invokeOnActiveTab(block: (AnalysisTabPanel) -> Unit) {
        SwingUtilities.invokeLater {
            val idx = activeTabIndex
            if (idx in 0 until tabbedPane.tabCount) block(getTabPanel(idx))
        }
    }

    /** 탭 헤더: 제목 Label + × 버튼 */
    private fun makeCloseableHeader(title: String, tabIndex: Int): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        panel.isOpaque = false

        val label = JLabel(title).apply {
            font = font.deriveFont(12f)
        }
        val closeBtn = JButton("×").apply {
            preferredSize = Dimension(16, 16)
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            font = font.deriveFont(Font.BOLD, 12f)
            toolTipText = "탭 닫기"
            addActionListener {
                val i = tabbedPane.indexOfTabComponent(panel)
                if (i > 0) {
                    tabbedPane.removeTabAt(i)
                    if (activeTabIndex >= tabbedPane.tabCount) {
                        activeTabIndex = tabbedPane.tabCount - 1
                    }
                }
            }
        }
        panel.add(label)
        panel.add(closeBtn)
        return panel
    }

    // ── 탭 내부 패널 ───────────────────────────────────────────────

    inner class AnalysisTabPanel : JPanel(BorderLayout()) {

        private val resultPane = JEditorPane().apply {
            contentType = "text/html"
            isEditable  = false
            background  = null
            putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
            font = Font("Arial", Font.PLAIN, 13)
        }
        private val scrollPane = JBScrollPane(resultPane).apply { border = null }

        fun showWelcome() {
            swapCenter(scrollPane)
            html("""
                <html>
                <body style="font-family:Arial,sans-serif;padding:28px 24px;text-align:center;">
                  <br/>
                  <p style="font-size:28px;margin:0;">🔍</p>
                  <h2 style="margin:10px 0 4px;color:#2B6CB0;">Niku Agent</h2>
                  <p style="color:#888;margin:4px 0 20px;font-size:12px;">프론트엔드 코드 흐름 분석기</p>
                  <p style="font-size:13px;line-height:1.6;">
                    분석할 파일을 열고<br/>
                    <b>우클릭 → Analyze with Niku Agent</b><br/>
                    또는 <b>Ctrl+Alt+N</b>
                  </p>
                  <p style="color:#aaa;font-size:11px;margin-top:20px;">React · TypeScript · JavaScript</p>
                </body></html>
            """.trimIndent())
        }

        fun showOptions(context: FileContext, onAnalyze: (String?) -> Unit) {
            swapCenter(buildOptionsPanel(context, onAnalyze))
        }

        fun showLoading() {
            swapCenter(scrollPane)
            html("""
                <html><body style="font-family:Arial,sans-serif;padding:28px;text-align:center;">
                  <br/><p style="font-size:28px;">⏳</p>
                  <p style="color:#2B6CB0;font-size:14px;font-weight:bold;">Claude에 요청 중...</p>
                  <p style="color:#aaa;font-size:12px;">응답이 시작되면 실시간으로 표시됩니다.</p>
                </body></html>
            """.trimIndent())
        }

        fun showStreaming(accumulated: String) {
            swapCenter(scrollPane)
            SwingUtilities.invokeLater {
                val esc = accumulated
                    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                resultPane.text = """
                    <html><body style="font-family:monospace;padding:12px;color:#333;">
                    <pre style="white-space:pre-wrap;font-size:12px;line-height:1.5;margin:0;">$esc<span style="color:#2B6CB0;">▌</span></pre>
                    </body></html>
                """.trimIndent()
                resultPane.caretPosition = maxOf(0, resultPane.document.length - 1)
            }
        }

        fun showResult(html: String) {
            swapCenter(scrollPane)
            html(html)
            SwingUtilities.invokeLater { resultPane.caretPosition = 0 }
        }

        fun showLoginRequired(onRetry: (() -> Unit)?) {
            swapCenter(buildLoginPanel(onRetry))
        }

        private fun swapCenter(comp: java.awt.Component) {
            SwingUtilities.invokeLater {
                removeAll(); add(comp, BorderLayout.CENTER); revalidate(); repaint()
            }
        }

        private fun html(h: String) = SwingUtilities.invokeLater { resultPane.text = h }
    }

    // ── 옵션 패널 (컴팩트) ─────────────────────────────────────────

    private fun buildOptionsPanel(context: FileContext, onAnalyze: (String?) -> Unit): JPanel {
        val root = JPanel()
        root.layout = BoxLayout(root, BoxLayout.Y_AXIS)
        root.border = BorderFactory.createEmptyBorder(10, 12, 10, 12)

        // ── 파일 정보 (1줄) ─────────────────────────────────────
        val fileInfo = buildString {
            append("<b>${context.fileName}</b>")
            append("  <span style='color:gray;font-size:11px;'>${context.language}</span>")
            when {
                context.focusFunctionName != null ->
                    append("  <span style='color:#555;'>🎯 ${context.focusFunctionName}</span>")
                context.selection != null ->
                    append("  <span style='color:#555;'>✂️ ${context.selection.lines().size}줄 선택</span>")
                else ->
                    append("  <span style='color:#555;'>📄 전체 파일</span>")
            }
        }
        val fileLabel = JBLabel("<html>$fileInfo</html>")
        fileLabel.alignmentX = LEFT_ALIGNMENT
        root.add(fileLabel)
        root.add(Box.createVerticalStrut(8))

        // ── 분석 유형 라디오 ────────────────────────────────────
        val standardRadio = JRadioButton("표준 분석").apply { isSelected = true; font = font.deriveFont(12f) }
        val customRadio   = JRadioButton("커스텀 질문").apply { font = font.deriveFont(12f) }
        ButtonGroup().apply { add(standardRadio); add(customRadio) }

        val radioRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            alignmentX = LEFT_ALIGNMENT
            isOpaque = false
            add(standardRadio); add(customRadio)
        }
        root.add(radioRow)

        // ── 커스텀 프롬프트 입력란 (초기 숨김) ──────────────────
        val promptArea = JBTextArea(3, 40).apply {
            lineWrap = true
            wrapStyleWord = true
            font = font.deriveFont(12f)
            emptyText.text = "예: 이 컴포넌트의 렌더링 조건을 정리해줘"
        }
        val promptScroll = JBScrollPane(promptArea).apply {
            isVisible = false
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 90)
            preferredSize = Dimension(400, 72)
        }
        root.add(Box.createVerticalStrut(4))
        root.add(promptScroll)

        customRadio.addActionListener {
            promptScroll.isVisible = true
            root.revalidate(); root.repaint()
            promptArea.requestFocusInWindow()
        }
        standardRadio.addActionListener {
            promptScroll.isVisible = false
            root.revalidate(); root.repaint()
        }

        root.add(Box.createVerticalStrut(8))

        // ── 분석 시작 버튼 ──────────────────────────────────────
        val analyzeBtn = JButton("▶  분석 시작").apply {
            font = Font(font.name, Font.BOLD, 12)
            background = Color(43, 108, 176)
            foreground = Color.WHITE
            isFocusPainted = false
        }
        analyzeBtn.addActionListener {
            val prompt = if (customRadio.isSelected)
                promptArea.text.trim().takeIf { it.isNotBlank() } else null
            onAnalyze(prompt)
        }
        val btnRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false; alignmentX = LEFT_ALIGNMENT; add(analyzeBtn)
        }
        root.add(btnRow)

        return root
    }

    // ── 로그인 패널 ────────────────────────────────────────────────

    private fun buildLoginPanel(onRetry: (() -> Unit)?): JPanel {
        val root = JPanel()
        root.layout = BoxLayout(root, BoxLayout.Y_AXIS)
        root.border = BorderFactory.createEmptyBorder(20, 20, 20, 20)

        val icon = JLabel("🔐").apply {
            font = font.deriveFont(30f); alignmentX = CENTER_ALIGNMENT
        }
        val heading = JBLabel("로그인이 필요합니다").apply {
            font = Font(font.name, Font.BOLD, 15); alignmentX = CENTER_ALIGNMENT
        }
        val desc = JBLabel("<html><div style='text-align:center;color:gray;font-size:12px;'>" +
            "Settings → Tools → Niku Agent에서<br/>로그인 버튼을 클릭해주세요." +
            "</div></html>").apply { alignmentX = CENTER_ALIGNMENT }

        root.add(Box.createVerticalStrut(8))
        root.add(icon)
        root.add(Box.createVerticalStrut(8))
        root.add(heading)
        root.add(Box.createVerticalStrut(6))
        root.add(desc)

        if (onRetry != null) {
            root.add(Box.createVerticalStrut(14))
            val retryBtn = JButton("↩  로그인 완료 — 다시 분석하기").apply {
                alignmentX = CENTER_ALIGNMENT
                addActionListener { onRetry() }
            }
            root.add(JPanel(FlowLayout(FlowLayout.CENTER)).apply { add(retryBtn) })
        }

        return root
    }
}
