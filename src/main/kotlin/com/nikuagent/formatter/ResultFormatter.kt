package com.nikuagent.formatter

import com.nikuagent.context.FileContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * LLM 응답(Markdown)을 JEditorPane 표시용 HTML로 변환한다.
 *
 * 출력 스타일:
 *  - 섹션 헤더: 굵은 파란색 (## → h2, ### → h3)
 *  - 인라인 코드: 회색 배경 pill 스타일
 *  - 코드 블록: 어두운 배경 pre 박스
 *  - 상단 메타 정보 바 (파일명, 언어, 시각)
 */
object ResultFormatter {

    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

    fun format(rawResponse: String, context: FileContext): String {
        val meta  = buildMeta(context)
        val body  = markdownToHtml(rawResponse)
        val time  = LocalDateTime.now().format(TIME_FMT)

        return """
            <html>
            <head><style>
              body  { font-family: Arial, sans-serif; font-size: 13px;
                      margin: 0; padding: 0; color: #1a1a1a; }
              .meta { background: #f0f4ff; padding: 8px 14px;
                      border-bottom: 1px solid #d0d8ee; font-size: 11px; color: #555; }
              .meta b { color: #2B6CB0; }
              .content { padding: 14px 16px; }
              h2    { font-size: 14px; font-weight: bold; color: #1a3a6b;
                      margin: 18px 0 5px; padding: 0; border: none; }
              h3    { font-size: 13px; font-weight: bold; color: #553C9A;
                      margin: 12px 0 4px; }
              p     { margin: 4px 0 8px; line-height: 1.6; }
              code  { font-family: monospace; font-size: 11px;
                      background: #eef2ff; color: #2B6CB0;
                      padding: 1px 5px; }
              pre   { font-family: monospace; font-size: 11px;
                      background: #f5f5f5; color: #1a1a1a;
                      padding: 10px 12px; margin: 8px 0;
                      white-space: pre-wrap; word-wrap: break-word; }
              ul    { margin: 4px 0 8px 18px; padding: 0; }
              ol    { margin: 4px 0 8px 18px; padding: 0; }
              li    { margin-bottom: 3px; line-height: 1.55; }
              hr    { border: none; border-top: 1px solid #e0e0e0; margin: 12px 0; }
              .tag  { font-family: monospace; font-size: 11px;
                      background: #fff0e6; color: #c05621;
                      padding: 1px 5px; }
              .warn { color: #c05621; font-style: italic; }
              .ok   { color: #276749; }
            </style></head>
            <body>
              <div class="meta">$meta &nbsp;·&nbsp; $time</div>
              <div class="content">$body</div>
            </body></html>
        """.trimIndent()
    }

    // ── 메타 정보 ────────────────────────────────────────────────

    private fun buildMeta(context: FileContext): String {
        val target = when {
            context.focusFunctionName != null -> "🎯 ${context.focusFunctionName}"
            context.selection != null         -> "✂️ ${context.selection.lines().size}줄 선택"
            else                              -> "📄 전체 파일"
        }
        return "<b>${context.fileName}</b> &nbsp;${context.language} &nbsp;·&nbsp; $target"
    }

    // ── Markdown → HTML 변환 ─────────────────────────────────────

    private fun markdownToHtml(md: String): String {
        var html = md
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        // 코드 블록 (``` ... ```) — 먼저 치환 (인라인 코드보다 우선)
        html = html.replace(Regex("```[\\w]*\\n([\\s\\S]*?)```", RegexOption.MULTILINE)) { m ->
            "<pre>${m.groupValues[1].trimEnd()}</pre>"
        }

        // H2 / H3
        html = html.replace(Regex("^## (.+)$", RegexOption.MULTILINE))  { "<h2>${it.groupValues[1]}</h2>" }
        html = html.replace(Regex("^### (.+)$", RegexOption.MULTILINE)) { "<h3>${it.groupValues[1]}</h3>" }

        // JSDoc 스타일 태그 (@param, @returns 등) → .tag 스타일
        html = html.replace(Regex("`(@[a-zA-Z]+)`")) {
            "<span class=\"tag\">${it.groupValues[1]}</span>"
        }

        // 인라인 코드
        html = html.replace(Regex("`([^`]+)`")) { "<code>${it.groupValues[1]}</code>" }

        // 굵게
        html = html.replace(Regex("\\*\\*(.+?)\\*\\*")) { "<b>${it.groupValues[1]}</b>" }

        // 이탤릭
        html = html.replace(Regex("(?<![*])\\*([^*\n]+)\\*(?![*])")) { "<i>${it.groupValues[1]}</i>" }

        // HR
        html = html.replace(Regex("^---$", RegexOption.MULTILINE), "<hr/>")

        // ✅ / ❌ 강조
        html = html.replace(Regex("✅ (.+)")) { "<span class=\"ok\">✅ ${it.groupValues[1]}</span>" }
        html = html.replace(Regex("⚠️ (.+)")) { "<span class=\"warn\">⚠️ ${it.groupValues[1]}</span>" }

        // 순서 있는 목록 & 없는 목록을 블록으로 처리
        html = buildLists(html)

        // 나머지 줄바꿈 처리
        html = html
            .replace(Regex("\n\n+"), "<br/>")
            .replace("\n", "<br/>")

        return html
    }

    /**
     * 연속된 `- item` 라인을 `<ul>`, `1. item` 라인을 `<ol>` 로 감싼다.
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
