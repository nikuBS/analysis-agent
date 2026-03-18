package com.nikuagent.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.FormBuilder
import com.nikuagent.service.CliLlmClient
import java.awt.BorderLayout
import java.awt.Dialog
import java.awt.FlowLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JDialog
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

    /** 진행 중인 claude /login 프로세스 (취소 시 종료) */
    @Volatile private var loginProcess: Process? = null
    /** 진행 중인 로그인 팝업 다이얼로그 */
    @Volatile private var loginDialog: JDialog? = null

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
        loginDialog?.dispose()
        loginDialog = null
        panel = null
        binaryPathField = null
        modelCombo = null
        statusLabel = null
    }

    // ── 로그인 플로우 ────────────────────────────────────────────────

    /**
     * `claude /login` 프로세스를 백그라운드에서 실행한다.
     * - JCEF 지원 환경: IDE 내 팝업 브라우저로 OAuth 진행 (외부 브라우저 불필요)
     * - JCEF 미지원: OS 기본 브라우저로 fallback
     */
    private fun startLoginFlow(
        binaryPath: String,
        statusLabel: JBLabel,
        loginButton: JButton,
        cancelButton: JButton,
    ) {
        loginProcess?.destroyForcibly()
        loginDialog?.dispose()

        SwingUtilities.invokeLater {
            statusLabel.text = "⏳ 인증 창을 여는 중..."
            loginButton.isEnabled = false
            cancelButton.isVisible = true
        }

        Thread {
            try {
                val proc = ProcessBuilder(binaryPath, "/login")
                    .redirectErrorStream(true)
                    .start()
                loginProcess = proc

                val sb         = StringBuilder()
                val ansiRegex  = Regex("""\u001B\[[0-9;]*[A-Za-z]""")
                val urlRegex   = Regex("""https://\S{10,}""")
                var urlHandled = false

                // stdout을 읽으며 OAuth URL 탐색
                val readerThread = Thread {
                    proc.inputStream.bufferedReader().use { reader ->
                        val buf = CharArray(256)
                        while (true) {
                            val n = reader.read(buf)
                            if (n < 0) break
                            synchronized(sb) { sb.append(buf, 0, n) }

                            if (!urlHandled) {
                                val clean = ansiRegex.replace(synchronized(sb) { sb.toString() }, "")
                                val url   = urlRegex.find(clean)?.value?.trimEnd(')', '.', ',')
                                if (url != null) {
                                    urlHandled = true
                                    SwingUtilities.invokeLater {
                                        if (JBCefApp.isSupported()) {
                                            showJcefLoginDialog(url, proc, statusLabel, loginButton, cancelButton)
                                        } else {
                                            openBrowser(url)
                                            statusLabel.text = "🌐 브라우저에서 로그인을 완료해주세요..."
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                readerThread.isDaemon = true
                readerThread.start()

                val exitCode = runCatching {
                    proc.waitFor(3, TimeUnit.MINUTES); proc.exitValue()
                }.getOrDefault(-1)
                loginProcess = null
                readerThread.join(2_000)

                // JCEF 모드에서는 다이얼로그가 완료를 처리하므로 여기서는 팝업만 닫음
                SwingUtilities.invokeLater { loginDialog?.dispose(); loginDialog = null }

                if (!urlHandled) {
                    // URL 파싱 실패 — claude가 자체적으로 브라우저를 열었을 수 있음
                    val output = synchronized(sb) { sb.toString() }
                    val finalStatus = when {
                        exitCode == 0 -> "✅ 로그인 완료! 이제 분석을 실행할 수 있습니다."
                        else          -> "⚠️ 인증 URL을 찾지 못했습니다 (코드: $exitCode). 터미널에서 `claude /login`을 실행해주세요.\n출력: $output"
                    }
                    SwingUtilities.invokeLater {
                        statusLabel.text = finalStatus
                        loginButton.isEnabled = true
                        cancelButton.isVisible = false
                    }
                } else if (!JBCefApp.isSupported()) {
                    // 시스템 브라우저 fallback 모드에서 프로세스 완료 처리
                    val output = synchronized(sb) { sb.toString() }
                    val finalStatus = when {
                        exitCode == 0 -> "✅ 로그인 완료! 이제 분석을 실행할 수 있습니다."
                        output.contains("error", ignoreCase = true) -> "❌ 로그인 실패. 다시 시도해주세요."
                        exitCode == -1 -> "⚠️ 로그인이 취소되었습니다."
                        else -> "⚠️ 종료 코드: $exitCode"
                    }
                    SwingUtilities.invokeLater {
                        statusLabel.text = finalStatus
                        loginButton.isEnabled = true
                        cancelButton.isVisible = false
                    }
                }
                // JCEF 모드에서는 showJcefLoginDialog 내 windowClosed에서 처리
            } catch (e: Exception) {
                loginProcess = null
                SwingUtilities.invokeLater {
                    statusLabel.text = "❌ 로그인 실패: ${e.message}"
                    loginButton.isEnabled = true
                    cancelButton.isVisible = false
                }
            }
        }.apply { isDaemon = true; name = "niku-login" }.start()
    }

    /**
     * IDE 내장 Chromium(JCEF) 팝업으로 OAuth 로그인 창을 표시한다.
     * claude /login 의 로컬 콜백 서버가 인증을 받으면 proc이 종료되고,
     * windowClosed 이벤트에서 상태를 업데이트한다.
     */
    private fun showJcefLoginDialog(
        url: String,
        proc: Process,
        statusLabel: JBLabel,
        loginButton: JButton,
        cancelButton: JButton,
    ) {
        val browser = JBCefBrowser(url)

        val dialog = JDialog(
            SwingUtilities.getWindowAncestor(panel),
            "Claude 로그인",
            Dialog.ModalityType.MODELESS,
        )
        dialog.contentPane.add(browser.component, BorderLayout.CENTER)
        dialog.setSize(960, 700)
        dialog.setLocationRelativeTo(SwingUtilities.getWindowAncestor(panel))

        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                // 사용자가 직접 닫거나 취소한 경우
                proc.destroyForcibly()
                loginProcess = null
                loginDialog  = null
                SwingUtilities.invokeLater {
                    statusLabel.text = "⚠️ 로그인 창을 닫았습니다. 다시 시도하려면 버튼을 클릭해주세요."
                    loginButton.isEnabled  = true
                    cancelButton.isVisible = false
                }
            }
            override fun windowClosed(e: WindowEvent) = windowClosing(e)
        })

        loginDialog = dialog

        // 별도 스레드에서 proc 종료를 감지해 다이얼로그를 자동으로 닫음
        Thread {
            val exitCode = runCatching {
                proc.waitFor(3, TimeUnit.MINUTES); proc.exitValue()
            }.getOrDefault(-1)

            val finalStatus = when (exitCode) {
                0    -> "✅ 로그인 완료! 이제 분석을 실행할 수 있습니다."
                -1   -> "⚠️ 로그인이 취소되었습니다."
                else -> "❌ 로그인 실패 (코드: $exitCode). 다시 시도해주세요."
            }
            SwingUtilities.invokeLater {
                // windowClosing이 중복 실행되지 않도록 리스너 제거 후 dispose
                dialog.windowListeners.forEach { dialog.removeWindowListener(it) }
                dialog.dispose()
                loginDialog  = null
                loginProcess = null
                statusLabel.text      = finalStatus
                loginButton.isEnabled  = true
                cancelButton.isVisible = false
            }
        }.apply { isDaemon = true; name = "niku-login-watcher" }.start()

        dialog.isVisible = true
    }

    // ── 로그인 상태 확인 ─────────────────────────────────────────────

    /**
     * claude --print 으로 짧은 프롬프트를 보내고
     * "Not logged in" 여부를 빠르게 확인한다.
     * 응답 첫 청크가 오거나 에러가 감지되면 즉시 프로세스를 종료한다.
     */
    private fun checkLoginStatus(binaryPath: String): String {
        var proc: Process? = null
        return try {
            proc = ProcessBuilder(binaryPath, "--print")
                .redirectErrorStream(true)
                .start()

            proc.outputStream.bufferedWriter().use { it.write("1+1=?") }

            val sb = StringBuilder()
            var earlyResult: String? = null

            val readerThread = Thread {
                try {
                    proc.inputStream.bufferedReader().use { reader ->
                        val buf = CharArray(512)
                        while (true) {
                            val n = reader.read(buf)
                            if (n < 0) break
                            synchronized(sb) { sb.append(buf, 0, n) }
                            val current = synchronized(sb) { sb.toString() }
                            when {
                                current.contains("Not logged in", ignoreCase = true) ||
                                current.contains("Please run /login", ignoreCase = true) -> {
                                    earlyResult = "❌ 로그인되어 있지 않습니다. '로그인' 버튼을 클릭해주세요."
                                    proc.destroyForcibly(); break
                                }
                                current.isNotBlank() -> {
                                    earlyResult = "✅ 로그인 상태 정상"
                                    proc.destroyForcibly(); break
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            readerThread.isDaemon = true
            readerThread.start()

            proc.waitFor(8, TimeUnit.SECONDS)
            proc.destroyForcibly()
            readerThread.join(2_000)

            earlyResult ?: when {
                synchronized(sb) { sb.toString() }.contains("Not logged in", ignoreCase = true) ->
                    "❌ 로그인되어 있지 않습니다. '로그인' 버튼을 클릭해주세요."
                synchronized(sb) { sb.isNotEmpty() } -> "✅ 로그인 상태 정상"
                else -> "⚠️ 응답 없음 — CLI 경로를 확인해주세요."
            }
        } catch (e: Exception) {
            "❌ 확인 실패: ${e.message}"
        } finally {
            runCatching { proc?.destroyForcibly() }
        }
    }

    /**
     * macOS: `open URL`, Linux: `xdg-open URL` 으로 시스템 기본 브라우저를 연다.
     * Desktop API 대신 OS 커맨드를 직접 사용해 WebStorm JVM 환경에서도 안정적으로 동작한다.
     */
    private fun openBrowser(url: String) {
        val os = System.getProperty("os.name", "").lowercase()
        val cmd = when {
            os.contains("mac")  -> arrayOf("open", url)
            os.contains("win")  -> arrayOf("cmd", "/c", "start", url)
            else                -> arrayOf("xdg-open", url)
        }
        runCatching { Runtime.getRuntime().exec(cmd) }
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
