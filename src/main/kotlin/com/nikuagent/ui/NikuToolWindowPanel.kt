package com.nikuagent.ui

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.nikuagent.context.FileContext
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.SwingUtilities

/**
 * Niku Agent Tool Window 메인 패널.
 *
 * JTabbedPane 대신 커스텀 탭 바를 직접 구현 (IntelliJ L&F 영향 없음).
 *
 * 구조:
 *  - NORTH : 커스텀 TabBar (JPanel + FlowLayout)
 *  - CENTER: 컨텐츠 영역 (CardLayout)
 */
class NikuToolWindowPanel : JPanel(BorderLayout()) {

    // ── 다크 테마 팔레트 ──────────────────────────────────────────
    companion object {
        val BG             = Color(0x1e1e1e)   // 본문 배경
        val BG_PANEL       = Color(0x252526)   // 패널 배경
        val BG_TAB_ACTIVE  = Color(0x1e1e1e)   // 선택된 탭 배경
        val BG_TAB_IDLE    = Color(0x2d2d2d)   // 비선택 탭 배경
        val BG_TABBAR      = Color(0x252526)   // 탭 바 배경
        val BG_INPUT       = Color(0x3c3c3c)   // 입력란 배경
        val FG             = Color(0xcccccc)   // 기본 텍스트
        val FG_DIM         = Color(0x888888)   // 비선택 탭 텍스트
        val FG_CLOSE       = Color(0x777777)   // 닫기 버튼
        val ACCENT         = Color(0x569cd6)   // 강조 파랑
        val BTN_BG         = Color(0x0e639c)   // 분석 버튼 배경
        val BTN_FG         = Color(0xffffff)   // 분석 버튼 텍스트
        val BORDER         = Color(0x3c3c3c)   // 구분선
    }

    // ── 탭 데이터 ────────────────────────────────────────────────

    private data class TabEntry(
        val id: Int,
        val contentPanel: AnalysisTabPanel,
        val headerPanel: JPanel,
        val titleLabel: JLabel,
    )

    private val tabs    = mutableListOf<TabEntry>()
    private var nextId  = 0
    private var activeId: Int = -1

    // ── UI 컴포넌트 ──────────────────────────────────────────────

    /** 탭 헤더 버튼들이 나란히 나열되는 상단 바 */
    private val tabBar = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
        background  = BG_TABBAR
        isOpaque    = true
        minimumSize = Dimension(0, 34)
        border      = BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER)
    }

    /** CardLayout 으로 탭 내용을 교체하는 중앙 영역 */
    private val contentArea = JPanel(CardLayout()).apply {
        background = BG
        isOpaque   = true
    }

    init {
        background = BG
        border     = null

        add(tabBar,     BorderLayout.NORTH)
        add(contentArea, BorderLayout.CENTER)

        addWelcomeTab()
    }

    // ── Public API ────────────────────────────────────────────────

    fun showOptions(context: FileContext, onAnalyze: (customPrompt: String?) -> Unit) {
        SwingUtilities.invokeLater {
            val id = addAnalysisTab(context)
            activeId = id
            getContent(id).showOptions(context, onAnalyze)
        }
    }

    fun showLoading()                  = invokeOnActive { it.showLoading() }
    fun showStreaming(text: String)    = invokeOnActive { it.showStreaming(text) }
    fun showLoginRequired(retry: (() -> Unit)? = null) = invokeOnActive { it.showLoginRequired(retry) }

    fun showResult(html: String) {
        invokeOnActive { it.showResult(html) }
        SwingUtilities.invokeLater { stripSpinner(activeId) }
    }

    // ── 탭 추가/삭제/선택 ─────────────────────────────────────────

    private fun addWelcomeTab() {
        val panel = AnalysisTabPanel().also { it.showWelcome() }
        addTabInternal("🏠 홈", panel, closeable = false)
    }

    private fun addAnalysisTab(context: FileContext): Int {
        val panel = AnalysisTabPanel()
        val title = buildTabTitle(context)
        return addTabInternal("⏳ $title", panel, closeable = true)
    }

    private fun addTabInternal(title: String, panel: AnalysisTabPanel, closeable: Boolean): Int {
        val id = nextId++

        val titleLabel = JLabel(title).apply {
            foreground = FG_DIM
            font       = Font("Arial", Font.PLAIN, 12)
        }

        val header = buildTabHeader(id, titleLabel, closeable)

        val entry = TabEntry(id, panel, header, titleLabel)
        tabs.add(entry)

        contentArea.add(panel, id.toString())
        tabBar.add(header)
        tabBar.revalidate()
        tabBar.repaint()

        selectTab(id)
        return id
    }

    private fun selectTab(id: Int) {
        activeId = id
        (contentArea.layout as CardLayout).show(contentArea, id.toString())

        for (tab in tabs) {
            val active = tab.id == id
            applyTabStyle(tab, active)
        }
        tabBar.revalidate()
        tabBar.repaint()
    }

    private fun removeTab(id: Int) {
        val idx = tabs.indexOfFirst { it.id == id }
        if (idx < 0) return
        val tab = tabs[idx]

        tabBar.remove(tab.headerPanel)
        contentArea.remove(tab.contentPanel)
        tabs.removeAt(idx)
        tabBar.revalidate()
        tabBar.repaint()
        contentArea.revalidate()
        contentArea.repaint()

        if (activeId == id && tabs.isNotEmpty()) {
            selectTab(tabs[maxOf(0, idx - 1)].id)
        }
    }

    // ── 탭 헤더 스타일 ─────────────────────────────────────────────

    private fun buildTabHeader(id: Int, label: JLabel, closeable: Boolean): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = true
            cursor   = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            add(label)
        }

        if (closeable) {
            val closeLabel = JLabel("  ×").apply {
                foreground = FG_CLOSE
                font       = Font("Arial", Font.BOLD, 13)
                cursor     = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            }
            closeLabel.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    removeTab(id)
                    e.consume()
                }
                override fun mouseEntered(e: MouseEvent) { closeLabel.foreground = FG }
                override fun mouseExited(e: MouseEvent)  { closeLabel.foreground = FG_CLOSE }
            })
            panel.add(closeLabel)
        }

        panel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) { selectTab(id) }
        })

        return panel
    }

    private fun applyTabStyle(tab: TabEntry, active: Boolean) {
        tab.headerPanel.background = if (active) BG_TAB_ACTIVE else BG_TAB_IDLE
        tab.titleLabel.foreground  = if (active) FG else FG_DIM

        tab.headerPanel.border = if (active) {
            // 선택된 탭: 상단 강조 파란 줄 + 좌우 구분선
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(2, 1, 0, 1, ACCENT),
                BorderFactory.createEmptyBorder(5, 10, 6, 8),
            )
        } else {
            // 비선택 탭: 우측 구분선만
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER),
                BorderFactory.createEmptyBorder(7, 10, 6, 8),
            )
        }
        tab.headerPanel.repaint()
    }

    // ── 유틸 ──────────────────────────────────────────────────────

    private fun buildTabTitle(ctx: FileContext): String = when {
        ctx.focusFunctionName != null -> "${ctx.fileName} › ${ctx.focusFunctionName}"
        ctx.selection != null         -> "${ctx.fileName} › ${ctx.selection.lines().size}줄"
        else                          -> ctx.fileName
    }

    /** 분석 완료 시 탭 제목의 ⏳ 제거 */
    private fun stripSpinner(id: Int) {
        tabs.find { it.id == id }?.titleLabel?.let {
            it.text = it.text.removePrefix("⏳ ")
        }
    }

    private fun getContent(id: Int) = tabs.first { it.id == id }.contentPanel

    private fun invokeOnActive(block: (AnalysisTabPanel) -> Unit) {
        SwingUtilities.invokeLater {
            tabs.find { it.id == activeId }?.let { block(it.contentPanel) }
        }
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
            border              = null
            background          = BG
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

        val root = JPanel().apply {
            layout     = BoxLayout(this, BoxLayout.Y_AXIS)
            border     = BorderFactory.createEmptyBorder(12, 14, 12, 14)
            background = BG_PANEL
            isOpaque   = true
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
            border     = BorderFactory.createEmptyBorder(0, 0, 6, 0)
        }
        root.add(fileLabel)

        // 구분선
        root.add(Box.createVerticalStrut(2))
        root.add(JPanel().apply {
            maximumSize   = Dimension(Int.MAX_VALUE, 1)
            preferredSize = Dimension(0, 1)
            background    = BORDER; isOpaque = true
        })
        root.add(Box.createVerticalStrut(8))

        // 라디오 버튼
        val standardRadio = JRadioButton("표준 분석").apply {
            isSelected = true; font = font.deriveFont(12f)
            background = BG_PANEL; foreground = FG; isOpaque = true
        }
        val customRadio = JRadioButton("커스텀 질문").apply {
            font = font.deriveFont(12f)
            background = BG_PANEL; foreground = FG; isOpaque = true
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
            isVisible     = false
            alignmentX    = LEFT_ALIGNMENT
            maximumSize   = Dimension(Int.MAX_VALUE, 88)
            preferredSize = Dimension(400, 72)
            border        = BorderFactory.createLineBorder(BORDER)
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
            font              = Font(font.name, Font.BOLD, 12)
            background        = BTN_BG; foreground = BTN_FG
            isFocusPainted    = false; isBorderPainted = false; isOpaque = true
        }
        analyzeBtn.addActionListener {
            val prompt = if (customRadio.isSelected)
                promptArea.text.trim().takeIf { it.isNotBlank() } else null
            onAnalyze(prompt)
        }
        root.add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            alignmentX = LEFT_ALIGNMENT; background = BG_PANEL; isOpaque = true; add(analyzeBtn)
        })

        return root
    }

    // ── 로그인 패널 (다크 고정) ───────────────────────────────────

    private fun buildLoginPanel(onRetry: (() -> Unit)?): JPanel {
        val root = JPanel().apply {
            layout     = BoxLayout(this, BoxLayout.Y_AXIS)
            border     = BorderFactory.createEmptyBorder(24, 20, 20, 20)
            background = BG; isOpaque = true
        }

        root.add(JLabel("🔐").apply {
            font       = font.deriveFont(30f); alignmentX = CENTER_ALIGNMENT
        })
        root.add(Box.createVerticalStrut(10))
        root.add(JBLabel("로그인이 필요합니다").apply {
            font       = Font(font.name, Font.BOLD, 15)
            foreground = FG; alignmentX = CENTER_ALIGNMENT
        })
        root.add(Box.createVerticalStrut(6))
        root.add(JBLabel(
            "<html><div style='text-align:center;color:#888;font-size:12px;'>" +
            "Settings → Tools → Niku Agent 에서<br/>로그인 버튼을 클릭해주세요." +
            "</div></html>"
        ).apply { alignmentX = CENTER_ALIGNMENT })

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
