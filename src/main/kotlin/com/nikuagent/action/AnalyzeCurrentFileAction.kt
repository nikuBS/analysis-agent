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
 * 3. Tool Window를 열고 로딩 상태 표시
 * 4. 백그라운드에서 [NikuAgentService.analyze] 실행
 * 5. 완료 후 결과를 Tool Window에 표시
 */
class AnalyzeCurrentFileAction : AnAction() {

    // UI 관련 update()는 EDT에서, 무거운 작업은 BGT에서
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    /**
     * 액션 활성화 조건: 에디터가 열려 있고 파일이 존재해야 한다.
     */
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = editor != null && file != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        // 1. 컨텍스트 수집 (EDT에서 실행 — 에디터 상태 접근)
        val context = ContextCollector.collect(editor, file) ?: run {
            // TODO: 사용자에게 알림 표시
            return
        }

        // 2. Tool Window 열기 및 로딩 상태 표시
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("Niku Agent") ?: return
        toolWindow.show()

        val panel = toolWindow.contentManager.contents
            .firstOrNull()
            ?.component as? NikuToolWindowPanel
        panel?.showLoading()

        // 3. 백그라운드에서 분석 실행
        val agentService = ApplicationManager.getApplication().service<NikuAgentService>()

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Niku Agent: ${context.fileName} 분석 중...",
            /* canBeCancelled = */ true,
        ) {
            private var result: String = ""

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                result = agentService.analyze(context)
            }

            // 4. 완료 후 EDT에서 UI 업데이트
            override fun onSuccess() {
                panel?.showResult(result)
            }

            override fun onCancel() {
                panel?.showResult("<html><body style='padding:12px;'>분석이 취소되었습니다.</body></html>")
            }

            override fun onThrowable(error: Throwable) {
                panel?.showResult(
                    "<html><body style='color:red;padding:12px;'>오류: ${error.message}</body></html>"
                )
            }
        })
    }
}
