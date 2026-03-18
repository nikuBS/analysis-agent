package com.nikuagent.formatter

import com.nikuagent.context.FileContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * LLM 응답(Markdown)을 JEditorPane 표시용 다크 테마 HTML로 변환한다.
 *
 * 다크 배경(#1e1e1e) + VS Code / IntelliJ Darcula 계열 색상을 사용해
 * 어두운 IDE 테마에서도 텍스트가 선명하게 보인다.
 *
 * 색상 가이드:
 *  - 배경:         #1e1e1e
 *  - 본문 텍스트:   #d4d4d4
 *  - H2 (섹션):    #569cd6  (밝은 파랑)
 *  - H3:           #c586c0  (밝은 보라)
 *  - 인라인 코드:   #ce9178  on #2d2d2d
 *  - JSDoc 태그:    #4ec9b0  on #2d2d2d  (청록)
 *  - 코드 블록:     #d4d4d4  on #252526
 *  - 메타바:        #3c3c3c 배경
 *  - 강조(✅):      #6a9955  / 경고(⚠️): #ce9178
 */
object ResultFormatter {

    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

    fun format(rawResponse: String, context: FileContext): String {
        val meta = buildMeta(context)
        val body = markdownToHtml(rawResponse)
        val time = LocalDateTime.now().format(TIME_FMT)

        return """
            <html>
            <head><style>
              body  { font-family: Arial, sans-serif; font-size: 13px;
                      margin: 0; padding: 0;
                      background: #1e1e1e; color: #d4d4d4; }
              .meta { background: #2d2d2d; padding: 7px 14px;
                      border-bottom: 1px solid #3c3c3c;
                      font-size: 11px; color: #888888; }
              .meta b { color: #9cdcfe; }
              .content { padding: 14px 16px; }
              h2   { font-size: 14px; font-weight: bold; color: #569cd6;
                     margin: 18px 0 5px; padding: 0; }
              h3   { font-size: 13px; font-weight: bold; color: #c586c0;
                     margin: 12px 0 4px; }
              p    { margin: 4px 0 8px; line-height: 1.65; }
              code { font-family: monospace; font-size: 11px;
                     background: #2d2d2d; color: #ce9178;
                     padding: 1px 5px; }
              pre  { font-family: monospace; font-size: 11px;
                     background: #252526; color: #d4d4d4;
                     padding: 10px 12px; margin: 8px 0;
                     white-space: pre-wrap; word-wrap: break-word;
                     border-left: 3px solid #569cd6; }
              ul   { margin: 4px 0 8px 18px; padding: 0; }
              ol   { margin: 4px 0 8px 18px; padding: 0; }
              li   { margin-bottom: 3px; line-height: 1.6; }
              hr   { border: none; border-top: 1px solid #3c3c3c; margin: 12px 0; }
              b    { color: #dcdcaa; }
              .tag { font-family: monospace; font-size: 11px;
                     background: #2d2d2d; color: #4ec9b0; padding: 1px 5px; }
              .ok  { color: #6a9955; }
              .warn { color: #ce9178; }
            </style></head>
            <body>
              <div class="meta"><b>${escapeHtml(context.fileName)}</b>
                &nbsp;·&nbsp; ${escapeHtml(context.language)}
                &nbsp;·&nbsp; ${buildTarget(context)}
                &nbsp;·&nbsp; $time
              </div>
              <div class="content">$body</div>
            </body></html>
        """.trimIndent()
    }

    // ── 메타 정보 ────────────────────────────────────────────────

    private fun buildMeta(context: FileContext): String = buildTarget(context)

    private fun buildTarget(context: FileContext): String = when {
        context.focusFunctionName != null -> "🎯 ${escapeHtml(context.focusFunctionName!!)}"
        context.selection != null         -> "✂️ ${context.selection.lines().size}줄 선택"
        else                              -> "📄 전체 파일"
    }

    private fun escapeHtml(s: String) =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    // ── Markdown → HTML 변환 ─────────────────────────────────────

    private fun markdownToHtml(md: String): String {
        var html = escapeHtml(md)

        // 코드 블록 (``` ... ```) — 인라인 코드보다 먼저 처리
        html = html.replace(Regex("```[\\w]*\\n([\\s\\S]*?)```", RegexOption.MULTILINE)) { m ->
            "<pre>${m.groupValues[1].trimEnd()}</pre>"
        }

        // H2 / H3
        html = html.replace(Regex("^## (.+)$",  RegexOption.MULTILINE)) { "<h2>${it.groupValues[1]}</h2>" }
        html = html.replace(Regex("^### (.+)$", RegexOption.MULTILINE)) { "<h3>${it.groupValues[1]}</h3>" }

        // JSDoc 태그 (@param, @returns 등) — 인라인 코드보다 먼저
        html = html.replace(Regex("`(@[a-zA-Z]+)`")) {
            "<span class=\"tag\">${it.groupValues[1]}</span>"
        }

        // 인라인 코드
        html = html.replace(Regex("`([^`\n]+)`")) { "<code>${it.groupValues[1]}</code>" }

        // 굵게 / 이탤릭
        html = html.replace(Regex("\\*\\*(.+?)\\*\\*")) { "<b>${it.groupValues[1]}</b>" }
        html = html.replace(Regex("(?<![*])\\*([^*\n]+)\\*(?![*])")) { "<i>${it.groupValues[1]}</i>" }

        // HR
        html = html.replace(Regex("^---$", RegexOption.MULTILINE), "<hr/>")

        // ✅ / ⚠️ 색상 강조
        html = html.replace(Regex("✅ (.+)")) { "<span class=\"ok\">✅ ${it.groupValues[1]}</span>" }
        html = html.replace(Regex("⚠️ (.+)")) { "<span class=\"warn\">⚠️ ${it.groupValues[1]}</span>" }

        // 리스트 블록 변환
        html = buildLists(html)

        // 나머지 줄바꿈
        html = html
            .replace(Regex("\n\n+"), "<br/>")
            .replace("\n", "<br/>")

        return html
    }

    /**
     * 연속된 `- item` → `<ul><li>`, `1. item` → `<ol><li>` 로 변환한다.
     */
    private fun buildLists(html: String): String {
        val lines  = html.split("\n")
        val result = StringBuilder()
        var inUl   = false
        var inOl   = false

        for (line in lines) {
            when {
                line.matches(Regex("^- .+")) -> {
                    if (inOl) { result.append("</ol>"); inOl = false }
                    if (!inUl) { result.append("<ul>"); inUl = true }
                    result.append("<li>${line.removePrefix("- ")}</li>")
                }
                line.matches(Regex("^\\d+\\. .+")) -> {
                    if (inUl) { result.append("</ul>"); inUl = false }
                    if (!inOl) { result.append("<ol>"); inOl = true }
                    result.append("<li>${line.replace(Regex("^\\d+\\. "), "")}</li>")
                }
                else -> {
                    if (inUl) { result.append("</ul>"); inUl = false }
                    if (inOl) { result.append("</ol>"); inOl = false }
                    result.append(line).append("\n")
                }
            }
        }
        if (inUl) result.append("</ul>")
        if (inOl) result.append("</ol>")
        return result.toString()
    }
}
