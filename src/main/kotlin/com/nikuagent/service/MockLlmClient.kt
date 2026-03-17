package com.nikuagent.service

/**
 * 개발/테스트용 Mock LLM 클라이언트.
 *
 * 실제 네트워크 호출 없이 고정된 응답을 반환한다.
 * 실제 API 연동 시 이 클래스를 [OpenAiLlmClient] 등으로 교체한다.
 *
 * TODO: 실제 API 연동 시 교체 포인트
 *   1. OpenAiLlmClient 클래스 생성 (OkHttp + JSON 파싱)
 *   2. NikuAgentService에서 MockLlmClient → OpenAiLlmClient 교체
 *   3. API 키는 PluginSettings 또는 환경변수에서 주입
 */
class MockLlmClient : LlmClient {

    override fun complete(prompt: String, onChunk: ((String) -> Unit)?): String {
        // MVP: 실제 API 호출 대신 목업 응답 반환
        // 프롬프트 내용을 간단히 파싱해서 파일명 정도만 추출
        val fileName = extractFileName(prompt)
        return buildMockResponse(fileName)
    }

    private fun extractFileName(prompt: String): String {
        val regex = Regex("""파일명:\s*(.+)""")
        return regex.find(prompt)?.groupValues?.get(1)?.trim() ?: "분석 대상 파일"
    }

    private fun buildMockResponse(fileName: String): String = """
        ## 1. 기능 요약
        `$fileName` 파일은 사용자 목록을 조회하고 표시하는 React 컴포넌트입니다.
        필터 조건에 따라 API를 호출하고 결과를 테이블 형태로 렌더링합니다.

        ## 2. 주요 파일 / 컴포넌트
        - `$fileName` — 메인 목록 컴포넌트
        - `useUserList` — 데이터 페칭 커스텀 훅 (추정)
        - `UserTable` — 테이블 렌더링 자식 컴포넌트 (추정)
        - `userApi.ts` — API 호출 함수 모음 (추정)

        ## 3. 화면 동작 흐름
        1. 컴포넌트 마운트 시 `useEffect`로 초기 데이터 로드
        2. 필터 입력 변경 → `setFilter` 상태 업데이트
        3. 필터 변경 감지 → API 재호출
        4. 로딩 상태 표시 → 응답 수신 → 목록 렌더링
        5. 항목 클릭 → 상세 페이지 라우팅 또는 모달 오픈

        ## 4. 상태 / 데이터 흐름
        - `useState`: 로딩 상태, 에러 상태, 목록 데이터, 필터 조건
        - `useEffect`: 필터 변경 시 API 재호출 트리거
        - Props: 부모로부터 초기 필터값 수신 가능성 있음
        - Context: 인증 정보 또는 권한 데이터 사용 가능성 있음

        ## 5. API 연계
        - `GET /api/users?filter=...` — 사용자 목록 조회 (추정)
        - 응답: `{ data: User[], total: number }` 형태 (추정)
        - 에러 시 토스트 메시지 또는 에러 컴포넌트 표시

        ## 6. 예외 / 리스크
        - API 응답 지연 시 로딩 상태 처리 여부 확인 필요
        - 빈 목록 케이스(empty state) UI 처리 여부 확인 필요
        - 필터 초기화 로직이 URL 파라미터와 동기화되어 있는지 확인 필요
        - 컴포넌트 언마운트 후 비동기 상태 업데이트 발생 여부 주의

        ## 7. 확인 필요 사항
        - 실제 API 엔드포인트 경로 및 파라미터 스펙
        - 상태 관리 라이브러리 사용 여부 (Redux, Zustand, Recoil 등)
        - 관련 타입 정의 파일 (`types/user.ts` 등)
        - 라우팅 구조 (`react-router` 또는 `Next.js` 여부)

        ---
        _⚠️ 이 결과는 Mock 응답입니다. 실제 LLM API 연동 후 정확한 분석이 제공됩니다._

        ## 🔑 실제 분석을 시작하려면
        1. **Settings (⌘,)** → **Tools** → **Niku Agent**
        2. 사용할 **Provider** 선택 후 해당 **API Key** 입력
           - OpenAI: `gpt-4o`, `gpt-4o-mini` 등
           - Anthropic: `claude-sonnet-4-6`, `claude-haiku-4-5-20251001` 등
        3. **Apply** 후 다시 **Analyze with Niku Agent** 실행
    """.trimIndent()
}
