package com.nikuagent.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.nikuagent.service.CliLlmClient
import java.awt.FlowLayout
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Settings → Tools → Niku Agent 설정 화면.
 *
 * Claude CLI 방식:
 * - claude CLI 경로 (비워두면 자동 탐색)
 * - 로그인: Terminal에서 /login 실행, ~/.claude.json 파일 감시로 완료 감지
 * - 사용할 모델 드롭다운 (비워두면 CLI 기본값)
 */
class NikuSettingsConfigurable : Configurable {

    private var binaryPathField: JBTextField? = null
    private var modelCombo: JComboBox<String>? = null
    private var statusLabel: JBLabel? = null
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Niku Agent"

    override fun createComponent(): JComponent {
        binaryPathField = JBTextField().apply {
            columns = 40
            emptyText.text = "비워두면 자동 탐색 (/usr/local/bin/claude 등)"
        }

        modelCombo = JComboBox(CLI_MODELS)
        statusLabel = JBLabel(" ")

        // ── CLI 경로 버튼 ──────────────────────────────────────────────
        val detectButton = JButton("자동 감지").apply {
            addActionListener {
                val found = CliLlmClient.findBinary()
                if (found != null) {
                    binaryPathField!!.text = found
                    statusLabel!!.text = "✅ 감지됨: $found"
                } else {
                    statusLabel!!.text = "❌ claude CLI를 찾을 수 없습니다. 설치 후 다시 시도해주세요."
                }
            }
        }

        val checkButton = JButton("버전 확인").apply {
            addActionListener {
                val path = binaryPathField!!.text.trim()
                    .ifBlank { CliLlmClient.findBinary() ?: "" }
                if (path.isBlank()) {
                    statusLabel!!.text = "❌ CLI 경로를 먼저 입력하거나 자동 감지를 실행해주세요."
                    return@addActionListener
                }
                val version = CliLlmClient.checkVersion(path)
                statusLabel!!.text = if (version != null) "✅ $version" else "❌ 실행 실패 — 경로를 확인해주세요."
            }
        }

        // ── 로그인 영역 ────────────────────────────────────────────────
        val loginStatusLabel = JBLabel(" ")

        val loginButton      = JButton("🔑  로그인 (브라우저 인증)")
        val cancelLoginButton = JButton("취소").apply { isVisible = false }

        // 로그인 상태 확인: ~/.claude.json 파일만 읽으므로 EDT에서 즉시 처리
        val checkLoginButton = JButton("로그인 상태 확인").apply {
            addActionListener {
                loginStatusLabel.text = checkLoginStatus()
            }
        }

        loginButton.addActionListener {
            val path = binaryPathField!!.text.trim()
                .ifBlank { CliLlmClient.findBinary() ?: "" }
            if (path.isBlank()) {
                loginStatusLabel.text = "❌ CLI 경로를 먼저 설정해주세요."
                return@addActionListener
            }
            startLoginFlow(path, loginStatusLabel, loginButton, cancelLoginButton)
        }

        cancelLoginButton.addActionListener {
            SwingUtilities.invokeLater {
                loginStatusLabel.text = "⚠️ 로그인이 취소되었습니다."
                loginButton.isEnabled  = true
                cancelLoginButton.isVisible = false
            }
        }

        // ── 패널 조합 ──────────────────────────────────────────────────
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(detectButton); add(checkButton)
        }
        val loginButtonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(loginButton); add(cancelLoginButton); add(checkLoginButton)
        }

        panel = FormBuilder.createFormBuilder()
            .addComponent(JBLabel("<html><b>Claude CLI 설정</b><br/>" +
                "<span style='color:gray;font-size:11px;'>" +
                "claude CLI가 로그인된 상태여야 동작합니다. " +
                "(<a href='https://claude.ai/download'>설치 방법</a>)</span></html>"))
            .addSeparator()
            .addLabeledComponent(JBLabel("CLI 경로:"), binaryPathField!!, true)
            .addComponent(buttonPanel)
            .addComponent(statusLabel!!)
            .addSeparator()
            .addComponent(JBLabel("<html><b>로그인</b><br/>" +
                "<span style='color:gray;font-size:11px;'>" +
                "Terminal 창에서 claude 를 실행하고 /login 을 입력해주세요." +
                "</span></html>"))
            .addComponent(loginButtonPanel)
            .addComponent(loginStatusLabel)
            .addSeparator()
            .addLabeledComponent(JBLabel("모델:"), modelCombo!!, true)
            .addComponent(JBLabel("<html><span style='color:gray;font-size:11px;'>" +
                "비워두면 claude CLI 기본 모델을 사용합니다.</span></html>"))
            .addComponentFillVertically(JPanel(), 0)
            .panel

        // Settings 열릴 때 현재 로그인 상태 즉시 표시
        loginStatusLabel.text = checkLoginStatus()

        reset()
        return panel!!
    }

    // ── Configurable 인터페이스 ──────────────────────────────────────

    override fun isModified(): Boolean {
        val s = NikuSettings.getInstance().state
        return binaryPathField!!.text.trim() != (s.cliBinaryPath ?: "") ||
               modelCombo!!.selectedItem as String != (s.cliModel ?: CLI_MODELS[0])
    }

    override fun apply() {
        val s = NikuSettings.getInstance().state
        s.cliBinaryPath = binaryPathField!!.text.trim().ifBlank { null }
        s.cliModel = (modelCombo!!.selectedItem as String).takeIf { it != CLI_MODELS[0] }
    }

    override fun reset() {
        val s = NikuSettings.getInstance().state
        binaryPathField!!.text = s.cliBinaryPath ?: ""

        val savedModel = s.cliModel ?: CLI_MODELS[0]
        modelCombo!!.selectedItem =
            if (CLI_MODELS.contains(savedModel)) savedModel else CLI_MODELS[0]

        statusLabel!!.text = " "
    }

    override fun disposeUIResources() {
        panel = null
        binaryPathField = null
        modelCombo = null
        statusLabel = null
    }

    // ── 로그인 상태 확인 (파일 기반, 즉시 반환) ────────────────────────

    /**
     * ~/.claude.json 파일을 직접 읽어 로그인 상태를 즉시 반환한다.
     *
     * Claude CLI는 OAuth 인증 완료 시 ~/.claude.json 에 oauthAccount 블록을 기록한다.
     * accountUuid + emailAddress 가 모두 있으면 정상 로그인 상태로 판단한다.
     *
     * - subprocess 없음 → 즉시 반환, UI 블로킹 없음
     * - 터미널이 꺼져도 파일은 영구 유지 → 세션 재시작 후에도 정상 동작
     */
    fun checkLoginStatus(): String = try {
        val claudeJson = File(System.getProperty("user.home"), ".claude.json")
        when {
            !claudeJson.exists() ->
                "❌ 로그인되어 있지 않습니다. '로그인' 버튼을 클릭해주세요."
            else -> {
                val content = claudeJson.readText()
                val hasAccount = content.contains("\"oauthAccount\"") &&
                                 content.contains("\"accountUuid\"") &&
                                 content.contains("\"emailAddress\"")
                if (hasAccount) "✅ 로그인 상태 정상"
                else "❌ 로그인되어 있지 않습니다. '로그인' 버튼을 클릭해주세요."
            }
        }
    } catch (e: Exception) {
        "❌ 확인 실패: ${e.message}"
    }

    // ── 로그인 플로우 ────────────────────────────────────────────────

    /**
     * Terminal 앱에서 claude를 실행하고 ~/.claude.json 파일 변경을 WatchService로 감지한다.
     *
     * 배경:
     *  `/login` 명령은 TTY(실제 터미널)가 있어야 작동한다.
     *  JVM 서브프로세스는 non-TTY 파이프 I/O를 사용하므로 /login 을 인식하지 못한다.
     *
     * 처리 흐름:
     *  1. osascript → Terminal 창 열기 + claude 자동 실행
     *  2. "/login 입력 안내" UI 표시
     *  3. WatchService로 ~/.claude.json 변경 감시
     *  4. 파일 변경 감지 → checkLoginStatus() 즉시 확인
     *  5. 로그인 완료 확인 시 → "✅ 로그인 완료" 표시 + 감시 종료
     */
    private fun startLoginFlow(
        binaryPath: String,
        statusLabel: JBLabel,
        loginButton: JButton,
        cancelButton: JButton,
    ) {
        SwingUtilities.invokeLater {
            statusLabel.text = "⏳ Terminal 창을 여는 중..."
            loginButton.isEnabled  = false
            cancelButton.isVisible = true
        }

        Thread {
            try {
                // ① osascript로 Terminal 창 열기 + claude 실행
                val script = """
                    tell application "Terminal"
                        activate
                        do script "$binaryPath"
                    end tell
                """.trimIndent()

                val osaProc = ProcessBuilder("osascript", "-e", script)
                    .redirectErrorStream(true)
                    .start()
                val finished  = osaProc.waitFor(5, TimeUnit.SECONDS)
                val osaOutput = osaProc.inputStream.bufferedReader().readText().trim()
                val exitCode  = if (finished) osaProc.exitValue() else -1

                if (exitCode != 0) {
                    SwingUtilities.invokeLater {
                        statusLabel.text = "❌ Terminal을 열지 못했습니다: $osaOutput\n" +
                            "터미널에서 직접 `$binaryPath` 실행 후 `/login` 을 입력해주세요."
                        loginButton.isEnabled  = true
                        cancelButton.isVisible = false
                    }
                    return@Thread
                }

                // ② 안내 메시지 표시
                SwingUtilities.invokeLater {
                    statusLabel.text = "🖥️  Terminal 창에서 /login 을 입력하고 Enter를 누르세요.\n" +
                        "브라우저 인증 완료 후 자동으로 감지합니다..."
                }

                // ③ ~/.claude.json 파일 변경을 WatchService로 감시 (최대 5분)
                val homeDir      = File(System.getProperty("user.home"))
                val watchService = FileSystems.getDefault().newWatchService()
                homeDir.toPath().register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE,
                )

                val deadline = System.currentTimeMillis() + 5 * 60_000L
                var loggedIn = false

                try {
                    while (System.currentTimeMillis() < deadline) {
                        // 취소 버튼 확인
                        if (!cancelButton.isVisible) break

                        // 최대 2초 대기 후 이벤트 처리
                        val key = watchService.poll(2, TimeUnit.SECONDS) ?: continue

                        val claudeJsonChanged = key.pollEvents().any { event ->
                            (event.context() as? Path)?.toString() == ".claude.json"
                        }
                        key.reset()

                        if (claudeJsonChanged) {
                            // 파일이 완전히 쓰여질 때까지 잠깐 대기
                            Thread.sleep(200)
                            val status = checkLoginStatus()
                            if (status.startsWith("✅")) {
                                loggedIn = true
                                SwingUtilities.invokeLater {
                                    statusLabel.text = "✅ 로그인 완료! 이제 분석을 실행할 수 있습니다."
                                    loginButton.isEnabled  = true
                                    cancelButton.isVisible = false
                                }
                                break
                            }
                        }
                    }
                } finally {
                    runCatching { watchService.close() }
                }

                if (!loggedIn && cancelButton.isVisible) {
                    SwingUtilities.invokeLater {
                        statusLabel.text = "⏱️ 시간 초과. 로그인 완료 후 '로그인 상태 확인' 버튼을 눌러주세요."
                        loginButton.isEnabled  = true
                        cancelButton.isVisible = false
                    }
                }

            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    statusLabel.text = "❌ 오류: ${e.message}"
                    loginButton.isEnabled  = true
                    cancelButton.isVisible = false
                }
            }
        }.apply { isDaemon = true; name = "niku-login" }.start()
    }

    companion object {
        private val CLI_MODELS = arrayOf(
            "기본값 (CLI 설정 따름)",
            "claude-sonnet-4-6",
            "claude-opus-4-5-20251101",
            "claude-haiku-4-5-20251001",
            "claude-3-5-sonnet-20241022",
        )
    }
}
