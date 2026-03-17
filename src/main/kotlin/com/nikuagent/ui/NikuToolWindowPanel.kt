package com.nikuagent.ui

import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Font
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/**
 * Niku Agent Tool Window의 메인 패널.
 *
 * 구성:
 * - 상단: 안내 레이블
 * - 중앙: 분석 결과를 표시하는 HTML 렌더링 영역 (JEditorPane)
 * - 하단: (TODO) 추가 컨트롤 영역 확장 가능
 *
 * 상태 전환:
 * - 초기: 안내 메시지
 * - 로딩: 로딩 스피너 텍스트
 * - 결과: HTML 분석 결과
 */
class NikuToolWindowPanel : JPanel(BorderLayout()) {

    private val resultPane = JEditorPane().apply {
        contentType = "text/html"
        isEditable = false
        // IDE 배경색과 동기화
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

    /**
     * 초기 안내 화면을 표시한다.
     */
    fun showWelcome() {
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
     * 로딩 상태를 표시한다.
     * 액션 실행 직후 호출한다.
     */
    fun showLoading() {
        setHtml(
            """
            <html>
            <body style="font-family:Arial,sans-serif;padding:24px;
                         color:#CDD6F4;background:#1E1E2E;text-align:center;">
              <br/><br/>
              <p style="font-size:32px;">⏳</p>
              <p style="color:#89B4FA;font-size:15px;">분석 중입니다...</p>
              <p style="color:#6C7086;font-size:12px;">잠시만 기다려 주세요.</p>
            </body>
            </html>
            """.trimIndent()
        )
    }

    /**
     * 분석 결과 HTML을 표시한다.
     *
     * @param html  [ResultFormatter]가 생성한 HTML 문자열
     */
    fun showResult(html: String) {
        setHtml(html)
        // 스크롤을 최상단으로 이동
        SwingUtilities.invokeLater {
            resultPane.caretPosition = 0
        }
    }

    /**
     * EDT 안전하게 HTML을 설정한다.
     */
    private fun setHtml(html: String) {
        SwingUtilities.invokeLater {
            resultPane.text = html
        }
    }
}
