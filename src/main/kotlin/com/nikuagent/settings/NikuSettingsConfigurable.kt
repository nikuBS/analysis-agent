package com.nikuagent.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.nikuagent.service.CliLlmClient
import java.awt.FlowLayout
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
 * - 사용할 모델 드롭다운 (비워두면 CLI 기본값)
 * - 버전 확인 버튼
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

        val loginStatusLabel = JBLabel(" ")

        val loginButton = JButton("터미널에서 로그인 (claude /login)").apply {
            addActionListener {
                val path = binaryPathField!!.text.trim()
                    .ifBlank { CliLlmClient.findBinary() ?: "claude" }
                try {
                    Runtime.getRuntime().exec(arrayOf(
                        "osascript", "-e",
                        "tell application \"Terminal\" to activate",
                        "-e",
                        "tell application \"Terminal\" to do script \"$path /login\""
                    ))
                    loginStatusLabel.text = "✅ 터미널을 열었습니다. 로그인 후 다시 분석을 실행해주세요."
                } catch (e: Exception) {
                    loginStatusLabel.text = "❌ 터미널 실행 실패: 터미널에서 직접 `$path /login`을 실행해주세요."
                }
            }
        }

        val checkLoginButton = JButton("로그인 상태 확인").apply {
            addActionListener {
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
        }

        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            add(detectButton)
            add(checkButton)
        }

        val loginButtonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            add(loginButton)
            add(checkLoginButton)
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
            .addComponent(JBLabel("<html><b>로그인</b></html>"))
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

    /**
     * claude --print 으로 짧은 프롬프트를 보내고,
     * "Not logged in" 문자열이 나오면 즉시 프로세스를 종료한다.
     * 로그인된 경우 AI 응답이 오기 시작하면 바로 ✅ 처리하고 종료한다.
     * 전체 타임아웃은 8초.
     */
    private fun checkLoginStatus(binaryPath: String): String {
        var proc: Process? = null
        return try {
            proc = ProcessBuilder(binaryPath, "--print")
                .redirectErrorStream(true)
                .start()

            // stdin으로 프롬프트 전달 후 즉시 닫기
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
                                    earlyResult = "❌ 로그인되어 있지 않습니다. '터미널에서 로그인' 버튼을 클릭해주세요."
                                    proc.destroyForcibly()
                                    break
                                }
                                current.isNotBlank() -> {
                                    // 에러 없이 응답이 오기 시작했으면 로그인 OK
                                    earlyResult = "✅ 로그인 상태 정상"
                                    proc.destroyForcibly()
                                    break
                                }
                            }
                        }
                    }
                } catch (_: Exception) { /* 프로세스 강제 종료 후 예외는 무시 */ }
            }
            readerThread.isDaemon = true
            readerThread.start()

            // 최대 8초 대기
            proc.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)
            proc.destroyForcibly()
            readerThread.join(2_000)

            earlyResult ?: when {
                synchronized(sb) { sb.toString() }.contains("Not logged in", ignoreCase = true) ->
                    "❌ 로그인되어 있지 않습니다. '터미널에서 로그인' 버튼을 클릭해주세요."
                synchronized(sb) { sb.isNotEmpty() } -> "✅ 로그인 상태 정상"
                else -> "⚠️ 응답 없음 — CLI 경로를 확인해주세요."
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
