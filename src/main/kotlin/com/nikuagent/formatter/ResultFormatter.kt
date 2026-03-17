package com.nikuagent.formatter

import com.nikuagent.context.FileContext

/**
 * LLM 응답을 UI 표시용 텍스트로 가공한다.
 *
 * MVP: Markdown 텍스트를 JEditorPane에서 렌더링할 수 있는
 *      단순 HTML로 변환한다.
 *
 * TODO: 더 정교한 Markdown → HTML 변환이 필요하면
 *       CommonMark 라이브러리 도입 고려
 */
object ResultFormatter {

    /**
     * LLM 응답(Markdown)을 HTML로 변환한다.
     *
     * @param rawResponse LLM이 반환한 Markdown 텍스트
     * @param context     분석 대상 파일 컨텍스트 (헤더 표시용)
     * @return            JEditorPane에서 렌더링 가능한 HTML 문자열
     */
    fun format(rawResponse: String, context: FileContext): String {
        val header = buildHeader(context)
        val body = convertMarkdownToHtml(rawResponse)

        // JEditorPane 내장 CSS 파서는 지원 범위가 제한적:
        // border-radius, border-bottom/top/left, overflow-x, -apple-system 등 미지원
        return """
            <html>
            <head>
            <style>
              body { font-family: Arial, sans-serif; font-size: 13px; margin: 12px; }
              h2 { color: #2B6CB0; margin-top: 16px; margin-bottom: 4px; }
              h3 { color: #553C9A; }
              code { font-family: monospace; font-size: 12px; }
              pre { font-family: monospace; font-size: 12px; margin: 8px 0; }
              ul, ol { margin-left: 20px; }
              li { margin-bottom: 4px; }
              .header { margin-bottom: 12px; }
              .warning { color: #C05621; font-style: italic; }
            </style>
            </head>
            <body>
            $header
            $body
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * 분석 대상 파일 정보 헤더 HTML을 생성한다.
     */
    private fun buildHeader(context: FileContext): String {
        val targetLabel = if (context.selection != null) "선택 영역 분석" else "전체 파일 분석"
        return """
            <div class="header">
              <b>📄 ${context.fileName}</b> &nbsp;|&nbsp;
              <code>${context.language}</code> &nbsp;|&nbsp;
              $targetLabel
            </div>
        """.trimIndent()
    }

    /**
     * Markdown 텍스트를 HTML로 변환한다.
     * MVP 수준의 단순 변환 — 주요 Markdown 요소만 처리한다.
     *
     * TODO: CommonMark 라이브러리 도입 시 이 메서드를 교체
     */
    private fun convertMarkdownToHtml(markdown: String): String {
        return markdown
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            // 코드 블록 (``` ... ```)
            .replace(Regex("""```[\w]*\n([\s\S]*?)```""")) { match ->
                "<pre><code>${match.groupValues[1].trim()}</code></pre>"
            }
            // H2 제목
            .replace(Regex("""^## (.+)$""", RegexOption.MULTILINE)) { match ->
                "<h2>${match.groupValues[1]}</h2>"
            }
            // H3 제목
            .replace(Regex("""^### (.+)$""", RegexOption.MULTILINE)) { match ->
                "<h3>${match.groupValues[1]}</h3>"
            }
            // 인라인 코드 (`...`)
            .replace(Regex("""`([^`]+)`""")) { match ->
                "<code>${match.groupValues[1]}</code>"
            }
            // 굵게 (**...**)
            .replace(Regex("""\*\*(.+?)\*\*""")) { match ->
                "<b>${match.groupValues[1]}</b>"
            }
            // HR
            .replace(Regex("""^---$""", RegexOption.MULTILINE), "<hr/>")
            // 경고 텍스트
            .replace(Regex("""_⚠️(.+?)_""")) { match ->
                """<span class="warning">⚠️${match.groupValues[1]}</span>"""
            }
            // 줄바꿈을 <br> 또는 <p>로
            .replace(Regex("""\n\n"""), "<br/><br/>")
            .replace(Regex("""\n- """), "<br/>• ")
            .replace(Regex("""\n(\d+)\. """)) { match ->
                "<br/>${match.groupValues[1]}. "
            }
            .replace("\n", "<br/>")
    }
}
