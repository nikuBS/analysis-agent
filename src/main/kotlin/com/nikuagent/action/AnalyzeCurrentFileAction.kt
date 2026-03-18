package com.nikuagent.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.wm.ToolWindowManager
import com.nikuagent.context.ContextCollector
import com.nikuagent.context.FileContext
import com.nikuagent.service.NikuAgentService
import com.nikuagent.ui.NikuToolWindowPanel

/**
 * "Analyze with Niku Agent" 액션.
 *
 * 에디터 우클릭 메뉴 또는 Tools 메뉴에서 실행된다.
 * 단축키: Ctrl+Alt+N
 *
 * 동작 순서:
 * 1. 현재 에디터 및 파일 정보 추출
 * 2. [ContextCollector]로 컨텍스트 수집
 * 3. Tool Window를 열고 옵션 패널 표시 (분석 유형 선택 / 커스텀 프롬프트)
 * 4. 사용자가 "분석 시작" 클릭 → 백그라운드에서 [NikuAgentService.analyze] 실행
 * 5. 완료 후 결과를 Tool Window에 표시
 */
class AnalyzeCurrentFileAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = editor != null && file != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        // 1. 컨텍스트 수집 (EDT — 에디터 상태 접근)
        val context = ContextCollector.collect(editor, file) ?: return

        // 2. Tool Window 열기
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("Niku Agent") ?: return
        toolWindow.show()

        val panel = toolWindow.contentManager.contents
            .firstOrNull()
            ?.component as? NikuToolWindowPanel
            ?: return

        // 3. 옵션 패널 표시 — 사용자가 분석 유형을 선택하고 "분석 시작"을 누를 때까지 대기
        panel.showOptions(context) { customPrompt ->
            runAnalysis(project, panel, context, customPrompt)
        }
    }

    private fun runAnalysis(
        project: com.intellij.openapi.project.Project,
        panel: NikuToolWindowPanel,
        context: FileContext,
        customPrompt: String?,
    ) {
        panel.showLoading()

        val agentService = ApplicationManager.getApplication().service<NikuAgentService>()
        val taskTitle = buildString {
            append("Niku Agent: ${context.fileName} 분석 중")
            if (customPrompt != null) append(" (커스텀)")
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            taskTitle,
            /* canBeCancelled = */ true,
        ) {
            private var result: String = ""

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                result = agentService.analyze(context, customPrompt) { accumulated ->
                    indicator.text2 = "${accumulated.length}자 수신 중..."
                    panel.showStreaming(accumulated)
                }
            }

            override fun onSuccess() {
                panel.showResult(result)
            }

            override fun onCancel() {
                panel.showResult(
                    "<html><body style='padding:12px;'>분석이 취소되었습니다.</body></html>"
                )
            }

            override fun onThrowable(error: Throwable) {
                val msg = error.message ?: ""
                if (msg.contains("로그인이 필요합니다") || msg.contains("Not logged in", ignoreCase = true)) {
                    panel.showLoginRequired {
                        runAnalysis(project, panel, context, customPrompt)
                    }
                } else {
                    panel.showResult(
                        "<html><body style='color:red;padding:12px;'>오류: $msg</body></html>"
                    )
                }
            }
        })
    }
}
