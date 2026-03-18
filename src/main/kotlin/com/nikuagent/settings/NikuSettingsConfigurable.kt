package com.nikuagent.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.nikuagent.service.CliLlmClient
import java.awt.FlowLayout
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
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
 * - 로그인: 플러그인 내에서 직접 브라우저 OAuth 처리
 * - 사용할 모델 드롭다운 (비워두면 CLI 기본값)
 */
class NikuSettingsConfigurable : Configurable {

    private var binaryPathField: JBTextField? = null
    private var modelCombo: JComboBox<String>? = null
    private var statusLabel: JBLabel? = null
    private var panel: JPanel? = null

    /** 진행 중인 claude 로그인 프로세스 (취소 시 종료) */
    @Volatile private var loginProcess: Process? = null

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

        val loginButton = JButton("🔑  로그인 (브라우저 인증)")
        val cancelLoginButton = JButton("취소").apply { isVisible = false }
        val checkLoginButton = JButton("로그인 상태 확인")

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
            loginProcess?.destroyForcibly()
            loginProcess = null
            SwingUtilities.invokeLater {
                loginStatusLabel.text = "⚠️ 로그인이 취소되었습니다."
                loginButton.isEnabled = true
                cancelLoginButton.isVisible = false
            }
        }

        checkLoginButton.addActionListener {
            val path = binaryPathField!!.text.trim()
                .ifBlank { CliLlmClient.findBinary() ?: "" }
            if (path.isBlank()) {
                loginStatusLabel.text = "❌ CLI 경로를 먼저 설정해주세요."
                return@addActionListener
            }
            loginStatusLabel.text = "⏳ 확인 중..."
            Thread {
                val status = checkLoginStatus(path)
                SwingUtilities.invokeLater { loginStatusLabel.text = status }
            }.apply { isDaemon = true }.start()
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
                "버튼을 누르면 브라우저가 열립니다. 인증 완료 시 자동으로 상태가 갱신됩니다." +
                "</span></html>"))
            .addComponent(loginButtonPanel)
            .addComponent(loginStatusLabel)
            .addSeparator()
            .addLabeledComponent(JBLabel("모델:"), modelCombo!!, true)
            .addComponent(JBLabel("<html><span style='color:gray;font-size:11px;'>" +
                "비워두면 claude CLI 기본 모델을 사용합니다.</span></html>"))
            .addComponentFillVertically(JPanel(), 0)
            .panel

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
        loginProcess?.destroyForcibly()
        loginProcess = null
        panel = null
        binaryPathField = null
        modelCombo = null
        statusLabel = null
    }

    // ── 로그인 플로우 ────────────────────────────────────────────────

    /**
     * Terminal 앱에서 claude를 실행하고, 플러그인은 로그인 완료를 폴링으로 감지한다.
     *
     * 배경:
     *  `/login` 명령은 TTY(실제 터미널)가 있어야만 작동한다.
     *  JVM 서브프로세스는 가짜 파이프 I/O를 사용하기 때문에 non-TTY로 감지되어
     *  `/login` 슬래시 명령이 인식되지 않는다. (→ "Unknown skill: login")
     *
     * 처리 흐름:
     *  1. osascript로 Terminal 창 열기 → claude 자동 실행
     *  2. 플러그인 UI에 "/login 입력 안내" 표시
     *  3. 백그라운드에서 3초마다 로그인 상태 폴링
     *  4. 로그인 감지 시 → "✅ 로그인 완료" 자동 표시 + 폴링 중단
     */
    private fun startLoginFlow(
        binaryPath: String,
        statusLabel: JBLabel,
        loginButton: JButton,
        cancelButton: JButton,
    ) {
        loginProcess?.destroyForcibly()

        SwingUtilities.invokeLater {
            statusLabel.text = "⏳ Terminal 창을 여는 중..."
            loginButton.isEnabled = false
            cancelButton.isVisible = true
        }

        Thread {
            try {
                // ① osascript로 Terminal 창을 열고 claude 실행
                //    Terminal이 없으면 iTerm2 fallback
                val script = """
                    tell application "Terminal"
                        activate
                        do script "$binaryPath"
                    end tell
                """.trimIndent()

                val proc = ProcessBuilder("osascript", "-e", script)
                    .redirectErrorStream(true)
                    .start()
                val finished = proc.waitFor(5, TimeUnit.SECONDS)
                val osaOutput = proc.inputStream.bufferedReader().readText().trim()
                val exitCode  = if (finished) proc.exitValue() else -1

                if (exitCode != 0) {
                    SwingUtilities.invokeLater {
                        statusLabel.text = "❌ Terminal을 열지 못했습니다: $osaOutput\n" +
                            "터미널에서 직접 `$binaryPath` 실행 후 `/login`을 입력해주세요."
                        loginButton.isEnabled = true
                        cancelButton.isVisible = false
                    }
                    return@Thread
                }

                // ② 안내 메시지 표시
                SwingUtilities.invokeLater {
                    statusLabel.text = "🖥️  Terminal 창에서 /login 을 입력하고 Enter를 누르세요.\n" +
                        "브라우저 인증 완료 후 자동으로 감지합니다..."
                }

                // ③ 로그인 완료 폴링 (최대 5분, 3초 간격)
                val pollDeadline = System.currentTimeMillis() + 5 * 60_000L
                var loggedIn = false

                while (System.currentTimeMillis() < pollDeadline) {
                    Thread.sleep(3_000)

                    // 취소 버튼이 눌린 경우 (cancelButton.isVisible이 false면 취소됨)
                    if (!cancelButton.isVisible) break

                    val status = checkLoginStatus(binaryPath)
                    if (status.startsWith("✅")) {
                        loggedIn = true
                        SwingUtilities.invokeLater {
                            statusLabel.text = "✅ 로그인 완료! 이제 분석을 실행할 수 있습니다."
                            loginButton.isEnabled = true
                            cancelButton.isVisible = false
                        }
                        break
                    }
                }

                if (!loggedIn && cancelButton.isVisible) {
                    SwingUtilities.invokeLater {
                        statusLabel.text = "⏱️ 시간 초과. 로그인 완료 후 '로그인 상태 확인' 버튼을 눌러주세요."
                        loginButton.isEnabled = true
                        cancelButton.isVisible = false
                    }
                }

            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    statusLabel.text = "❌ 오류: ${e.message}"
                    loginButton.isEnabled = true
                    cancelButton.isVisible = false
                }
            }
        }.apply { isDaemon = true; name = "niku-login" }.start()
    }

    // ── 로그인 상태 확인 ─────────────────────────────────────────────

    /**
     * `claude --version` 실행 후 `claude --print`로 미로그인 메시지를 감지한다.
     * API 호출을 최소화하기 위해 "Not logged in" 판단만 하고 즉시 프로세스를 종료한다.
     */
    private fun checkLoginStatus(binaryPath: String): String {
        var proc: Process? = null
        return try {
            proc = ProcessBuilder(binaryPath, "--print")
                .redirectErrorStream(true)
                .start()

            // stdin 즉시 닫아 EOF 신호 — "--print" 모드는 stdin이 EOF이면 빈 입력으로 처리됨
            // 빈 입력 시 로그인 에러 메시지가 바로 출력되므로 별도 텍스트 전송 불필요
            proc.outputStream.close()

            val sb         = StringBuilder()
            val earlyResult = AtomicReference<String>(null)
            val done        = AtomicBoolean(false)

            val readerThread = Thread {
                try {
                    proc.inputStream.bufferedReader().use { reader ->
                        val buf = CharArray(512)
                        while (!done.get()) {
                            val n = reader.read(buf)
                            if (n < 0) break
                            synchronized(sb) { sb.append(buf, 0, n) }
                            val current = synchronized(sb) { sb.toString() }
                            when {
                                current.contains("Not logged in", ignoreCase = true) ||
                                current.contains("Please run /login", ignoreCase = true) ||
                                current.contains("not logged", ignoreCase = true) -> {
                                    earlyResult.set("❌ 로그인되어 있지 않습니다. '로그인' 버튼을 클릭해주세요.")
                                    done.set(true)
                                    proc.destroyForcibly()
                                    break
                                }
                                current.length > 5 -> {
                                    earlyResult.set("✅ 로그인 상태 정상")
                                    done.set(true)
                                    proc.destroyForcibly()
                                    break
                                }
                            }
                        }
                    }
                } catch (_: Exception) { done.set(true) }
            }
            readerThread.isDaemon = true
            readerThread.start()

            // 최대 10초 대기 — earlyResult 세팅 시 즉시 반환
            val deadline = System.currentTimeMillis() + 10_000
            while (earlyResult.get() == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
            }
            done.set(true)
            proc.destroyForcibly()
            readerThread.join(1_000)

            earlyResult.get() ?: run {
                val out = synchronized(sb) { sb.toString() }
                when {
                    out.contains("Not logged in", ignoreCase = true) ->
                        "❌ 로그인되어 있지 않습니다. '로그인' 버튼을 클릭해주세요."
                    out.isNotBlank() -> "✅ 로그인 상태 정상"
                    else -> "⚠️ 응답 없음 — CLI 경로를 확인하거나 다시 시도해주세요."
                }
            }
        } catch (e: Exception) {
            "❌ 확인 실패: ${e.message}"
        } finally {
            runCatching { proc?.destroyForcibly() }
        }
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
