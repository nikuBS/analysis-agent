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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
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
     * claude 인터랙티브 셸을 실행하고 stdin으로 `/login` 명령을 전송한다.
     * `/login`은 CLI 인자가 아닌 셸 내부 슬래시 명령이기 때문에 stdin으로 전달해야 한다.
     *
     * 처리 흐름:
     *  1. `claude` 프로세스 시작 → stdin에 `/login\n` 전송
     *  2. stdout에서 OAuth URL 탐색 (최대 20초)
     *  3. URL 발견 시 → JCEF 팝업 또는 시스템 브라우저로 표시
     *  4. URL 미발견 시 → 수집된 출력 내용을 표시해 원인 파악 가능하게 함
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
            statusLabel.text = "⏳ Claude CLI 시작 중..."
            loginButton.isEnabled = false
            cancelButton.isVisible = true
        }

        Thread {
            try {
                // ① claude 인터랙티브 셸 시작 (인자 없이)
                val pb = ProcessBuilder(binaryPath).redirectErrorStream(true)
                pb.environment()["TERM"] = "xterm-256color"
                val proc = pb.start()
                loginProcess = proc

                // ② stdin으로 /login 명령 전송 (스트림은 닫지 않음 — 셸이 살아있어야 함)
                val stdinWriter = proc.outputStream.bufferedWriter()
                stdinWriter.write("/login\n")
                stdinWriter.flush()

                val sb        = StringBuilder()
                val ansiRegex = Regex("""\u001B\[[0-9;]*[A-Za-z]|\r""")
                // 괄호·마침표 등 불필요한 trailing 문자를 제외한 URL 매칭
                val urlRegex  = Regex("""https://[A-Za-z0-9\-._~:/?#\[\]@!$&'*+,;=%]{15,}""")
                var urlHandled = false

                // ③ stdout 리더 스레드 — URL 탐색 + 실시간 상태 표시
                val readerThread = Thread {
                    try {
                        proc.inputStream.bufferedReader().use { reader ->
                            val buf = CharArray(512)
                            while (true) {
                                val n = reader.read(buf)
                                if (n < 0) break
                                synchronized(sb) { sb.append(buf, 0, n) }

                                val clean = ansiRegex.replace(synchronized(sb) { sb.toString() }, "")

                                // URL이 아직 처리되지 않은 경우 탐색
                                if (!urlHandled) {
                                    val url = urlRegex.find(clean)?.value
                                        ?.trimEnd(')', '.', ',', '\'', '"')
                                    if (url != null) {
                                        urlHandled = true
                                        SwingUtilities.invokeLater {
                                            if (JBCefApp.isSupported()) {
                                                statusLabel.text = "🌐 IDE 내 브라우저에서 로그인을 완료해주세요..."
                                                showJcefLoginDialog(url, proc, statusLabel, loginButton, cancelButton)
                                            } else {
                                                openBrowser(url)
                                                statusLabel.text = "🌐 브라우저에서 로그인을 완료해주세요. 완료 후 '로그인 상태 확인'을 눌러주세요."
                                            }
                                        }
                                    } else {
                                        // URL 미발견 시 수신 내용을 실시간으로 표시 (최대 120자)
                                        val preview = clean.trim().takeLast(120)
                                        if (preview.isNotBlank()) {
                                            SwingUtilities.invokeLater {
                                                statusLabel.text = "⏳ $preview"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
                readerThread.isDaemon = true
                readerThread.start()

                // ④ URL 발견 대기 (최대 20초)
                val deadline = System.currentTimeMillis() + 20_000
                while (!urlHandled && System.currentTimeMillis() < deadline && proc.isAlive) {
                    Thread.sleep(300)
                }

                if (!urlHandled) {
                    // URL을 찾지 못한 경우 — 실제 출력 내용을 사용자에게 보여줌
                    runCatching { stdinWriter.close() }
                    proc.destroyForcibly()
                    loginProcess = null
                    readerThread.join(2_000)

                    val raw = ansiRegex.replace(synchronized(sb) { sb.toString() }, "").trim()
                    SwingUtilities.invokeLater {
                        statusLabel.text = if (raw.isNotBlank()) {
                            "⚠️ 인증 URL을 찾지 못했습니다.\n" +
                            "CLI 출력: ${raw.take(300)}\n\n" +
                            "터미널에서 직접 `claude /login`을 실행해주세요."
                        } else {
                            "⚠️ Claude CLI가 응답하지 않습니다.\n" +
                            "CLI 경로가 올바른지 확인하고 터미널에서 `claude /login`을 실행해주세요."
                        }
                        loginButton.isEnabled = true
                        cancelButton.isVisible = false
                    }
                }
                // JCEF 모드: showJcefLoginDialog 내 watcher 스레드가 완료를 처리
                // 시스템 브라우저 모드: 사용자가 완료 후 '로그인 상태 확인'으로 검증

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
