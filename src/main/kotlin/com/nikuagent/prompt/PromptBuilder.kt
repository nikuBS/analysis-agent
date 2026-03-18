package com.nikuagent.prompt

import com.nikuagent.context.FileContext

/**
 * [FileContext]를 LLM에 전달할 프롬프트 문자열로 변환한다.
 *
 * 출력 포맷은 프론트엔드 업무 프로세스 분석 전용으로 구성된다.
 * TODO: 프롬프트 템플릿을 외부 파일(resources)로 분리하면 유지보수 용이
 */
object PromptBuilder {

    /**
     * 분석 요청 프롬프트를 생성한다.
     * 선택 영역이 있으면 해당 코드를, 없으면 전체 파일을 분석 대상으로 삼는다.
     */
    fun build(context: FileContext): String {
        val analysisTarget = if (context.selection != null) {
            buildSelectedCodeSection(context.selection)
        } else {
            buildFullFileSection(context.fullContent)
        }

        val importsSection = if (context.imports.isNotEmpty()) {
            buildImportsSection(context.imports)
        } else {
            ""
        }

        val focusInstruction = if (context.focusFunctionName != null) {
            "⚠️ 분석 집중 대상: `${context.focusFunctionName}` 함수(또는 변수/컴포넌트)에 집중하여 분석해주세요. 전체 파일 코드에서 해당 심볼의 정의와 호출 흐름을 추적하세요.\n|"
        } else {
            ""
        }

        return """
            |당신은 프론트엔드 시니어 개발자이자 코드 분석 전문가입니다.
            |아래 코드를 분석하여 업무 프로세스와 코드 동작 흐름을 구조화해서 설명해주세요.
            |
            |${focusInstruction}분석 대상 파일 정보:
            |- 파일명: ${context.fileName}
            |- 언어: ${context.language}
            |- 파일 경로: ${context.filePath}
            |
            |$importsSection
            |$analysisTarget
            |
            |다음 형식으로 분석 결과를 작성해주세요. 각 항목은 한국어로 작성합니다.
            |
            |## 1. 기능 요약
            |이 코드가 어떤 역할을 하는지 2~3줄로 요약하세요.
            |
            |## 2. 주요 파일 / 컴포넌트
            |관련된 주요 컴포넌트나 파일을 나열하고 각 역할을 설명하세요.
            |
            |## 3. 화면 동작 흐름
            |사용자 인터랙션부터 렌더링까지의 흐름을 단계별로 설명하세요.
            |(예: 버튼 클릭 → 핸들러 호출 → 상태 변경 → 리렌더링)
            |
            |## 4. 상태 / 데이터 흐름
            |useState, useReducer, Context, Redux 등의 상태 관리 흐름을 설명하세요.
            |props 전달 구조도 포함하세요.
            |
            |## 5. API 연계
            |fetch, axios, react-query, SWR 등 API 호출 지점과 엔드포인트를 정리하세요.
            |없으면 "없음"으로 표기하세요.
            |
            |## 6. 예외 / 리스크
            |에러 처리, 엣지 케이스, 잠재적 버그 가능성을 나열하세요.
            |
            |## 7. 확인 필요 사항
            |코드를 완전히 이해하기 위해 추가로 확인해야 할 파일이나 로직을 나열하세요.
        """.trimMargin()
    }

    private fun buildFullFileSection(content: String): String {
        val truncated = content.take(MAX_CONTENT_LENGTH)
        val note = if (content.length > MAX_CONTENT_LENGTH) "\n[주의: 파일이 길어 일부만 포함됨]" else ""
        return "분석할 전체 파일 코드:\n```\n$truncated$note\n```"
    }

    private fun buildSelectedCodeSection(selection: String): String {
        return "분석할 선택 코드:\n```\n$selection\n```"
    }

    /**
     * 사용자가 직접 입력한 프롬프트를 기반으로 분석 요청을 생성한다.
     * 파일(또는 선택 코드)을 컨텍스트로 포함하고, 사용자 질문을 주 지시로 사용한다.
     */
    fun buildCustom(context: FileContext, userPrompt: String): String {
        val codeSection = if (context.selection != null) {
            buildSelectedCodeSection(context.selection)
        } else {
            buildFullFileSection(context.fullContent)
        }

        val importsSection = if (context.imports.isNotEmpty()) buildImportsSection(context.imports) else ""

        val focusNote = if (context.focusFunctionName != null) {
            "특히 `${context.focusFunctionName}` 함수를 중심으로 답변해주세요.\n\n"
        } else {
            ""
        }

        return """
            |당신은 프론트엔드 시니어 개발자이자 코드 분석 전문가입니다.
            |아래 코드를 분석하여 두 가지를 한국어로 답변해주세요.
            |
            |분석 대상 파일 정보:
            |- 파일명: ${context.fileName}
            |- 언어: ${context.language}
            |- 파일 경로: ${context.filePath}
            |
            |$importsSection
            |$codeSection
            |
            |${focusNote}---
            |
            |다음 형식으로 표준 분석을 먼저 수행한 후, 추가 질문에도 답변하세요.
            |
            |## 1. 기능 요약
            |이 코드가 어떤 역할을 하는지 2~3줄로 요약하세요.
            |
            |## 2. 주요 파일 / 컴포넌트
            |관련된 주요 컴포넌트나 파일을 나열하고 각 역할을 설명하세요.
            |
            |## 3. 화면 동작 흐름
            |사용자 인터랙션부터 렌더링까지의 흐름을 단계별로 설명하세요.
            |
            |## 4. 상태 / 데이터 흐름
            |useState, useReducer, Context, Redux 등의 상태 관리 흐름을 설명하세요.
            |
            |## 5. API 연계
            |fetch, axios, react-query, SWR 등 API 호출 지점과 엔드포인트를 정리하세요.
            |없으면 "없음"으로 표기하세요.
            |
            |## 6. 예외 / 리스크
            |에러 처리, 엣지 케이스, 잠재적 버그 가능성을 나열하세요.
            |
            |## 7. 확인 필요 사항
            |코드를 완전히 이해하기 위해 추가로 확인해야 할 파일이나 로직을 나열하세요.
            |
            |---
            |
            |## 💬 추가 질문 답변
            |$userPrompt
        """.trimMargin()
    }

    private fun buildImportsSection(imports: List<String>): String {
        return "파일의 Import 목록:\n```\n${imports.joinToString("\n")}\n```\n"
    }

}

// LLM 토큰 한도 대비 최대 컨텐츠 길이 (문자 기준)
// TODO: 실제 API 연동 시 모델별 토큰 한도에 맞게 조정
private const val MAX_CONTENT_LENGTH = 8_000
