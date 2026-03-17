package com.nikuagent.context

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile

/**
 * 에디터 상태로부터 [FileContext]를 수집한다.
 *
 * 책임:
 * - 현재 열린 파일 경로/이름/언어 추출
 * - 파일 전체 텍스트 읽기
 * - 선택 영역 텍스트 추출
 * - import 구문 간단 파싱 (MVP: 정규식 기반)
 *
 * TODO: 추후 PSI(Program Structure Interface) 기반으로 교체하면
 *       더 정교한 심볼 추적이 가능하다.
 */
object ContextCollector {

    /**
     * 에디터와 가상 파일 정보를 기반으로 [FileContext]를 생성한다.
     *
     * @param editor  현재 활성 에디터
     * @param file    현재 열린 가상 파일
     * @return        수집된 [FileContext], 실패 시 null
     */
    fun collect(editor: Editor, file: VirtualFile): FileContext? {
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
        val fullContent = document.text

        val selectionModel = editor.selectionModel
        val rawSelection = if (selectionModel.hasSelection()) {
            selectionModel.selectedText?.takeIf { it.isNotBlank() }
        } else {
            null
        }

        // 공백·줄바꿈 없는 단일 식별자(함수명 등)만 선택된 경우를 감지
        val isSingleIdentifier = rawSelection != null &&
                !rawSelection.contains('\n') &&
                !rawSelection.contains(' ') &&
                rawSelection.matches(Regex("""[A-Za-z_$][A-Za-z0-9_$]*"""))

        val selection = if (isSingleIdentifier) null else rawSelection
        val focusFunctionName = if (isSingleIdentifier) rawSelection else null

        val language = detectLanguage(file.extension)
        val imports = extractImports(fullContent)

        return FileContext(
            filePath = file.path,
            fileName = file.name,
            language = language,
            fullContent = fullContent,
            selection = selection,
            focusFunctionName = focusFunctionName,
            imports = imports,
        )
    }

    /**
     * 파일 확장자를 언어 이름으로 변환한다.
     */
    private fun detectLanguage(extension: String?): String = when (extension?.lowercase()) {
        "tsx" -> "TypeScript (React)"
        "ts"  -> "TypeScript"
        "jsx" -> "JavaScript (React)"
        "js"  -> "JavaScript"
        "vue" -> "Vue"
        else  -> extension?.uppercase() ?: "Unknown"
    }

    /**
     * 파일 내용에서 import 구문을 간단하게 추출한다.
     *
     * MVP: 정규식 기반 파싱. 복잡한 동적 import는 누락될 수 있다.
     * TODO: PSI 기반으로 교체하면 정확도 향상 가능
     */
    private fun extractImports(content: String): List<String> {
        val importRegex = Regex("""^import\s+.+$""", RegexOption.MULTILINE)
        return importRegex.findAll(content)
            .map { it.value.trim() }
            .take(30) // 과도한 컨텍스트 전송 방지
            .toList()
    }
}
