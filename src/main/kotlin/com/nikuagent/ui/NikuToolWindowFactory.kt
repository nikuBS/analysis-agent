package com.nikuagent.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Niku Agent Tool Window 팩토리.
 *
 * plugin.xml에 등록되어 IDE 시작 시 Tool Window를 초기화한다.
 * [DumbAware]: 인덱싱 중에도 Tool Window를 사용 가능하게 한다.
 */
class NikuToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = NikuToolWindowPanel()
        val content = ContentFactory.getInstance()
            .createContent(panel, /* displayName = */ "", /* isLockable = */ false)
        toolWindow.contentManager.addContent(content)
    }
}
