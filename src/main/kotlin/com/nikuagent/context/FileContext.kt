package com.nikuagent.context

/**
 * 분석 대상 파일의 컨텍스트 정보를 담는 데이터 클래스.
 *
 * @param filePath    절대 파일 경로
 * @param fileName    파일명 (예: UserList.tsx)
 * @param language    언어 식별자 (예: TypeScript, JavaScript)
 * @param fullContent 파일 전체 텍스트
 * @param selection   에디터에서 선택된 텍스트. 선택 없으면 null
 * @param imports     파일 상단 import 구문 목록 (간단 파싱)
 *                    TODO: 정교한 PSI 기반 import 추적으로 교체 가능
 */
data class FileContext(
    val filePath: String,
    val fileName: String,
    val language: String,
    val fullContent: String,
    val selection: String? = null,
    val imports: List<String> = emptyList(),
)
