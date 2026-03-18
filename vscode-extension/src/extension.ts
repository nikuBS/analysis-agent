import * as vscode from 'vscode';
import { collect } from './contextCollector';
import { NikuPanel } from './nikuPanel';

export function activate(context: vscode.ExtensionContext): void {
  const cmd = vscode.commands.registerCommand('niku-agent.analyze', async () => {
    const editor = vscode.window.activeTextEditor;
    if (!editor) {
      vscode.window.showWarningMessage('Niku Agent: 분석할 파일을 에디터에서 열어주세요.');
      return;
    }

    const fileContext = await collect(editor);
    if (!fileContext) {
      vscode.window.showErrorMessage('Niku Agent: 컨텍스트 수집에 실패했습니다.');
      return;
    }

    NikuPanel.createOrShow(context.extensionUri);
    NikuPanel.currentPanel?.showOptions(fileContext);
  });

  context.subscriptions.push(cmd);
}

export function deactivate(): void {
  NikuPanel.currentPanel?.dispose();
}
