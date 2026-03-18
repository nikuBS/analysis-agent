import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';
import { FileContext } from './contextCollector';
import { CliLlmClient, LlmException } from './cliLlmClient';
import { PromptBuilder } from './promptBuilder';

// ── 웹뷰 ↔ 익스텐션 메시지 타입 ──────────────────────────────────

interface WebviewMsg {
  type: 'analyze' | 'retry' | 'ready';
  tabId?: number;
  customPrompt?: string | null;
}

/**
 * Niku Agent 웹뷰 패널 (싱글턴).
 *
 * 탭 관리는 웹뷰 HTML/JS 측에서 수행한다.
 * 익스텐션은 분석 요청/응답 메시지만 처리한다.
 */
export class NikuPanel {
  static currentPanel: NikuPanel | undefined;

  private readonly _panel: vscode.WebviewPanel;
  private readonly _extensionUri: vscode.Uri;
  private _disposables: vscode.Disposable[] = [];

  // tabId → FileContext 매핑
  private _contexts = new Map<number, FileContext>();
  private _nextTabId = 0;

  // ── 생성 / 표시 ──────────────────────────────────────────────

  static createOrShow(extensionUri: vscode.Uri): void {
    const column = vscode.window.activeTextEditor
      ? vscode.ViewColumn.Beside
      : vscode.ViewColumn.One;

    if (NikuPanel.currentPanel) {
      NikuPanel.currentPanel._panel.reveal(column);
      return;
    }

    const panel = vscode.window.createWebviewPanel(
      'nikuAgent',
      'Niku Agent',
      column,
      {
        enableScripts: true,
        retainContextWhenHidden: true,
        localResourceRoots: [vscode.Uri.joinPath(extensionUri, 'media')],
      },
    );

    NikuPanel.currentPanel = new NikuPanel(panel, extensionUri);
  }

  private constructor(panel: vscode.WebviewPanel, extensionUri: vscode.Uri) {
    this._panel = panel;
    this._extensionUri = extensionUri;
    this._panel.webview.html = this._buildHtml();

    this._panel.onDidDispose(() => this.dispose(), null, this._disposables);
    this._panel.webview.onDidReceiveMessage(
      (msg: WebviewMsg) => this._handleMessage(msg),
      null,
      this._disposables,
    );
  }

  dispose(): void {
    NikuPanel.currentPanel = undefined;
    this._panel.dispose();
    this._disposables.forEach(d => d.dispose());
    this._disposables = [];
  }

  // ── Public API (Action 에서 호출) ─────────────────────────────

  showOptions(context: FileContext): void {
    const tabId = this._nextTabId++;
    this._contexts.set(tabId, context);
    const title = buildTabTitle(context);

    this._post({ type: 'newTab', tabId, title, context });
  }

  // ── 웹뷰 수신 메시지 처리 ─────────────────────────────────────

  private _handleMessage(msg: WebviewMsg): void {
    if (msg.type === 'analyze' && msg.tabId !== undefined) {
      this._runAnalysis(msg.tabId, msg.customPrompt ?? null);
    }
    if (msg.type === 'retry' && msg.tabId !== undefined) {
      this._runAnalysis(msg.tabId, null);
    }
  }

  private _runAnalysis(tabId: number, customPrompt: string | null): void {
    const context = this._contexts.get(tabId);
    if (!context) return;

    this._post({ type: 'showLoading', tabId });

    const config = vscode.workspace.getConfiguration('nikuAgent');
    const binaryPath =
      (config.get<string>('cliBinaryPath') || '').trim() || CliLlmClient.findBinary();

    if (!binaryPath) {
      this._post({
        type: 'showError',
        tabId,
        message:
          'claude CLI를 찾을 수 없습니다.\n' +
          '설정에서 CLI 경로를 지정하거나, https://claude.ai/download 에서 설치해주세요.',
      });
      return;
    }

    const model = (config.get<string>('model') || '').trim() || undefined;
    const client = new CliLlmClient(binaryPath, model);

    const prompt = customPrompt
      ? PromptBuilder.buildCustom(context, customPrompt)
      : PromptBuilder.build(context);

    client
      .complete(prompt, (accumulated) => {
        this._post({ type: 'streaming', tabId, text: accumulated });
      })
      .then((raw) => {
        this._post({ type: 'result', tabId, markdown: raw, context });
      })
      .catch((err: unknown) => {
        const msg = err instanceof LlmException ? err.message : String(err);
        const isLoginError =
          msg.includes('로그인이 필요합니다') ||
          /not logged in/i.test(msg);

        if (isLoginError) {
          this._post({ type: 'loginRequired', tabId });
        } else {
          this._post({ type: 'showError', tabId, message: msg });
        }
      });
  }

  // ── HTML 생성 ─────────────────────────────────────────────────

  private _buildHtml(): string {
    const htmlPath = path.join(this._extensionUri.fsPath, 'media', 'panel.html');
    let html = fs.readFileSync(htmlPath, 'utf8');
    const nonce = getNonce();
    const cspSource = this._panel.webview.cspSource;
    return html
      .replace(/\$\{nonce\}/g, nonce)
      .replace(/\$\{cspSource\}/g, cspSource);
  }

  private _post(data: Record<string, unknown>): void {
    this._panel.webview.postMessage(data);
  }
}

// ── Helpers ───────────────────────────────────────────────────────

function buildTabTitle(ctx: FileContext): string {
  if (ctx.focusFunctionName) return `${ctx.fileName} › ${ctx.focusFunctionName}`;
  if (ctx.selection) return `${ctx.fileName} › ${ctx.selection.split('\n').length}줄`;
  return ctx.fileName;
}

function getNonce(): string {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  return Array.from({ length: 32 }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
}
