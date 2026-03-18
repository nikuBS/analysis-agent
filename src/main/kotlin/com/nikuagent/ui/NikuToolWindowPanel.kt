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
 * 다크 모드 고정 (배경 #1e1e1e / 텍스트 #cccccc).
 *
 * 구조:
 *  - CENTER: JTabbedPane
 *    - 탭 0 🏠 홈 (닫기 불가)
 *    - 탭 1~N 분석 결과 (닫기 가능, 제목 = 파일명 + 컨텍스트)
 */
class NikuToolWindowPanel : JPanel(BorderLayout()) {

    // ── 다크 테마 팔레트 ──────────────────────────────────────────
    companion object {
        val BG        = Color(0x1e1e1e)   // 본문 배경
        val BG_PANEL  = Color(0x252526)   // 패널 배경
        val BG_INPUT  = Color(0x3c3c3c)   // 입력란 배경
        val FG        = Color(0xcccccc)   // 기본 텍스트
        val FG_DIM    = Color(0x888888)   // 보조 텍스트
        val FG_CLOSE  = Color(0x999999)   // 닫기 버튼
        val ACCENT    = Color(0x569cd6)   // 강조 파랑
        val BTN_BG    = Color(0x0e639c)   // 분석 버튼 배경
        val BTN_FG    = Color(0xffffff)   // 분석 버튼 텍스트
        val BORDER    = Color(0x3c3c3c)   // 구분선
    }

    private val tabbedPane = JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT).apply {
        background = BG_PANEL
        foreground = FG
        isOpaque   = true
    }

    private var activeTabIndex: Int = 0

    init {
        background = BG
        border     = null
        add(tabbedPane, BorderLayout.CENTER)
        addWelcomeTab()
    }

    // ── Public API ────────────────────────────────────────────────

    fun showOptions(context: FileContext, onAnalyze: (customPrompt: String?) -> Unit) {
        SwingUtilities.invokeLater {
            val idx = addAnalysisTab(context)
            activeTabIndex = idx
            getTabPanel(idx).showOptions(context, onAnalyze)
        }
    }

    fun showLoading()                      = invokeOnActive { it.showLoading() }
    fun showStreaming(text: String)        = invokeOnActive { it.showStreaming(text) }
    fun showLoginRequired(retry: (() -> Unit)? = null) = invokeOnActive { it.showLoginRequired(retry) }

    fun showResult(html: String) {
        invokeOnActive { it.showResult(html) }
        SwingUtilities.invokeLater { finishTabTitle(activeTabIndex) }
    }

    // ── 탭 관리 ──────────────────────────────────────────────────

    private fun addWelcomeTab() {
        val panel = AnalysisTabPanel().also { it.showWelcome() }
        tabbedPane.addTab("🏠 홈", panel)
        // 홈 탭은 닫기 버튼 없이 일반 탭 컴포넌트 사용
        tabbedPane.setTabComponentAt(0, makePlainHeader("🏠 홈"))
    }

    private fun addAnalysisTab(context: FileContext): Int {
        val panel = AnalysisTabPanel()
        val title = buildTabTitle(context)
        val idx   = tabbedPane.tabCount
        tabbedPane.addTab(title, panel)
        tabbedPane.setTabComponentAt(idx, makeCloseableHeader("⏳ $title", closeable = true))
        tabbedPane.selectedIndex = idx
        return idx
    }

    private fun buildTabTitle(ctx: FileContext): String = when {
        ctx.focusFunctionName != null -> "${ctx.fileName} › ${ctx.focusFunctionName}"
        ctx.selection != null         -> "${ctx.fileName} › ${ctx.selection.lines().size}줄"
        else                          -> ctx.fileName
    }

    /** 분석 완료 시 탭 제목의 ⏳ 제거 */
    private fun finishTabTitle(idx: Int) {
        val comp  = tabbedPane.getTabComponentAt(idx) as? JPanel ?: return
        val label = comp.components.filterIsInstance<JLabel>().firstOrNull() ?: return
        label.text = label.text.removePrefix("⏳ ")
    }

    private fun getTabPanel(i: Int) = tabbedPane.getComponentAt(i) as AnalysisTabPanel

    private fun invokeOnActive(block: (AnalysisTabPanel) -> Unit) {
        SwingUtilities.invokeLater {
            val i = activeTabIndex
            if (i in 0 until tabbedPane.tabCount) block(getTabPanel(i))
        }
    }

    // ── 탭 헤더 컴포넌트 ─────────────────────────────────────────

    /** 닫기 버튼 없는 일반 탭 헤더 (홈 탭용) */
    private fun makePlainHeader(title: String): JPanel =
        JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque   = false
            background = BG_PANEL
            add(JLabel(title).apply {
                foreground = FG
                font       = font.deriveFont(12f)
            })
        }

    /**
     * 닫기(×) 버튼이 있는 탭 헤더.
     * 다크 배경 고정: foreground = #cccccc, 닫기 버튼 = #999999
     */
    private fun makeCloseableHeader(title: String, closeable: Boolean): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
            isOpaque   = false
            background = BG_PANEL
        }

        val label = JLabel(title).apply {
            foreground = FG
            font       = font.deriveFont(12f)
        }

        panel.add(label)

        if (closeable) {
            val closeBtn = JButton("×").apply {
                preferredSize       = Dimension(16, 16)
                isBorderPainted     = false
                isContentAreaFilled = false
                isFocusPainted      = false
                foreground          = FG_CLOSE
                font                = font.deriveFont(Font.BOLD, 13f)
                toolTipText         = "닫기"
                addActionListener {
                    val i = tabbedPane.indexOfTabComponent(panel)
                    if (i > 0) {
                        tabbedPane.removeTabAt(i)
                        if (activeTabIndex >= tabbedPane.tabCount)
                            activeTabIndex = tabbedPane.tabCount - 1
                    }
                }
            }
            panel.add(closeBtn)
        }

        return panel
    }

    // ── 탭 내용 패널 ─────────────────────────────────────────────

    inner class AnalysisTabPanel : JPanel(BorderLayout()) {

        private val resultPane = JEditorPane().apply {
            contentType = "text/html"
            isEditable  = false
            background  = BG
            foreground  = FG
            putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
            font = Font("Arial", Font.PLAIN, 13)
        }
        private val scrollPane = JBScrollPane(resultPane).apply {
            border          = null
            background      = BG
            viewport.background = BG
        }

        init { background = BG }

        // ── 상태별 표시 ──────────────────────────────────────────

        fun showWelcome() {
            swap(scrollPane)
            html("""
                <html><body style="font-family:Arial,sans-serif;padding:32px 24px;
                                   text-align:center;background:#1e1e1e;color:#cccccc;">
                  <br/>
                  <p style="font-size:30px;margin:0;">🔍</p>
                  <h2 style="margin:10px 0 4px;color:#569cd6;font-size:18px;">Niku Agent</h2>
                  <p style="color:#666;margin:2px 0 20px;font-size:12px;">프론트엔드 코드 흐름 분석기</p>
                  <p style="font-size:13px;line-height:1.8;color:#aaaaaa;">
                    분석할 파일을 열고<br/>
                    <b style="color:#dcdcaa;">우클릭 → Analyze with Niku Agent</b><br/>
                    또는 <b style="color:#dcdcaa;">Ctrl+Alt+N</b>
                  </p>
                  <p style="color:#444;font-size:11px;margin-top:24px;">
                    React · TypeScript · JavaScript
                  </p>
                </body></html>
            """.trimIndent())
        }

        fun showOptions(context: FileContext, onAnalyze: (String?) -> Unit) {
            swap(buildOptionsPanel(context, onAnalyze))
        }

        fun showLoading() {
            swap(scrollPane)
            html("""
                <html><body style="font-family:Arial,sans-serif;padding:32px;
                                   text-align:center;background:#1e1e1e;color:#cccccc;">
                  <br/><p style="font-size:30px;">⏳</p>
                  <p style="color:#569cd6;font-size:14px;font-weight:bold;margin:8px 0 4px;">
                    Claude에 요청 중...
                  </p>
                  <p style="color:#555;font-size:12px;">응답이 시작되면 실시간으로 표시됩니다.</p>
                </body></html>
            """.trimIndent())
        }

        fun showStreaming(accumulated: String) {
            swap(scrollPane)
            SwingUtilities.invokeLater {
                val esc = accumulated
                    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                resultPane.text = """
                    <html><body style="font-family:monospace;padding:14px;
                                       background:#1e1e1e;color:#d4d4d4;margin:0;">
                    <pre style="white-space:pre-wrap;font-size:12px;line-height:1.55;
                                margin:0;color:#d4d4d4;">$esc<span
                        style="color:#569cd6;font-weight:bold;">▌</span></pre>
                    </body></html>
                """.trimIndent()
                resultPane.caretPosition = maxOf(0, resultPane.document.length - 1)
            }
        }

        fun showResult(html: String) {
            swap(scrollPane)
            html(html)
            SwingUtilities.invokeLater { resultPane.caretPosition = 0 }
        }

        fun showLoginRequired(onRetry: (() -> Unit)?) {
            swap(buildLoginPanel(onRetry))
        }

        private fun swap(c: java.awt.Component) = SwingUtilities.invokeLater {
            removeAll(); add(c, BorderLayout.CENTER); revalidate(); repaint()
        }

        private fun html(h: String) = SwingUtilities.invokeLater { resultPane.text = h }
    }

    // ── 옵션 패널 (다크 고정) ─────────────────────────────────────

    private fun buildOptionsPanel(context: FileContext, onAnalyze: (String?) -> Unit): JPanel {

        fun JPanel.dark()  { background = BG_PANEL; isOpaque = true }
        fun JLabel.light() { foreground = FG }
        fun JRadioButton.darkStyle() {
            background = BG_PANEL; foreground = FG; isOpaque = true
        }

        val root = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(12, 14, 12, 14)
            background = BG_PANEL
            isOpaque = true
        }

        // 파일 정보 한 줄
        val target = when {
            context.focusFunctionName != null ->
                "<span style='color:#4ec9b0;'>🎯 ${context.focusFunctionName}</span>"
            context.selection != null ->
                "<span style='color:#ce9178;'>✂️ ${context.selection.lines().size}줄 선택</span>"
            else -> "<span style='color:#888;'>📄 전체 파일</span>"
        }
        val fileLabel = JBLabel(
            "<html><span style='color:#9cdcfe;font-weight:bold;'>${context.fileName}</span>" +
            " &nbsp;<span style='color:#666;font-size:11px;'>${context.language}</span>" +
            " &nbsp;$target</html>"
        ).apply {
            alignmentX = LEFT_ALIGNMENT
            border = BorderFactory.createEmptyBorder(0, 0, 6, 0)
        }
        root.add(fileLabel)

        // 구분선
        root.add(Box.createVerticalStrut(2))
        val sep = JPanel().apply {
            maximumSize = Dimension(Int.MAX_VALUE, 1)
            preferredSize = Dimension(0, 1)
            background = BORDER; isOpaque = true
        }
        root.add(sep)
        root.add(Box.createVerticalStrut(8))

        // 라디오 버튼
        val standardRadio = JRadioButton("표준 분석").apply {
            isSelected = true; font = font.deriveFont(12f); darkStyle()
        }
        val customRadio = JRadioButton("커스텀 질문").apply {
            font = font.deriveFont(12f); darkStyle()
        }
        ButtonGroup().apply { add(standardRadio); add(customRadio) }

        val radioRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            alignmentX = LEFT_ALIGNMENT; background = BG_PANEL; isOpaque = true
            add(standardRadio); add(customRadio)
        }
        root.add(radioRow)

        // 커스텀 프롬프트 입력란
        val promptArea = JBTextArea(3, 40).apply {
            lineWrap = true; wrapStyleWord = true
            font = font.deriveFont(12f)
            background = BG_INPUT; foreground = FG
            caretColor = FG
            emptyText.text = "예: 이 컴포넌트의 렌더링 조건을 정리해줘"
        }
        val promptScroll = JBScrollPane(promptArea).apply {
            isVisible = false
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 88)
            preferredSize = Dimension(400, 72)
            border = BorderFactory.createLineBorder(BORDER)
            viewport.background = BG_INPUT
        }
        root.add(Box.createVerticalStrut(6))
        root.add(promptScroll)

        customRadio.addActionListener {
            promptScroll.isVisible = true; root.revalidate(); root.repaint()
            promptArea.requestFocusInWindow()
        }
        standardRadio.addActionListener {
            promptScroll.isVisible = false; root.revalidate(); root.repaint()
        }

        root.add(Box.createVerticalStrut(10))

        // 분석 시작 버튼
        val analyzeBtn = JButton("▶  분석 시작").apply {
            font = Font(font.name, Font.BOLD, 12)
            background = BTN_BG; foreground = BTN_FG
            isFocusPainted = false; isBorderPainted = false; isOpaque = true
        }
        analyzeBtn.addActionListener {
            val prompt = if (customRadio.isSelected)
                promptArea.text.trim().takeIf { it.isNotBlank() } else null
            onAnalyze(prompt)
        }
        val btnRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            alignmentX = LEFT_ALIGNMENT; background = BG_PANEL; isOpaque = true; add(analyzeBtn)
        }
        root.add(btnRow)

        return root
    }

    // ── 로그인 패널 (다크 고정) ───────────────────────────────────

    private fun buildLoginPanel(onRetry: (() -> Unit)?): JPanel {
        val root = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(24, 20, 20, 20)
            background = BG; isOpaque = true
        }

        val icon = JLabel("🔐").apply {
            font = font.deriveFont(30f); alignmentX = CENTER_ALIGNMENT
        }
        val heading = JBLabel("로그인이 필요합니다").apply {
            font = Font(font.name, Font.BOLD, 15)
            foreground = FG; alignmentX = CENTER_ALIGNMENT
        }
        val desc = JBLabel(
            "<html><div style='text-align:center;color:#888;font-size:12px;'>" +
            "Settings → Tools → Niku Agent 에서<br/>로그인 버튼을 클릭해주세요." +
            "</div></html>"
        ).apply { alignmentX = CENTER_ALIGNMENT }

        root.add(icon)
        root.add(Box.createVerticalStrut(10))
        root.add(heading)
        root.add(Box.createVerticalStrut(6))
        root.add(desc)

        if (onRetry != null) {
            root.add(Box.createVerticalStrut(16))
            val retryBtn = JButton("↩  로그인 완료 — 다시 분석하기").apply {
                background = BTN_BG; foreground = BTN_FG
                isBorderPainted = false; isOpaque = true
                addActionListener { onRetry() }
            }
            root.add(JPanel(FlowLayout(FlowLayout.CENTER)).apply {
                background = BG; add(retryBtn)
            })
        }

        return root
    }
}
