import * as vscode from 'vscode';
import * as path from 'path';

/**
 * 분석 대상 파일의 컨텍스트 정보.
 * WebStorm 플러그인의 FileContext 와 동일한 구조.
 */
export interface FileContext {
  filePath: string;
  fileName: string;
  language: string;
  fullContent: string;
  selection: string | null;
  focusFunctionName: string | null;
  imports: string[];
}

/**
 * VS Code 에디터 상태로부터 FileContext 를 수집한다.
 * - 선택된 텍스트 / 단일 식별자(함수명) / 전체 파일 구분
 * - Document Symbol Provider 로 커서 위치의 함수명 보완
 */
export async function collect(editor: vscode.TextEditor): Promise<FileContext | null> {
  const document = editor.document;
  const fullContent = document.getText();
  const ext = path.extname(document.fileName).replace('.', '').toLowerCase();

  const rawSelection = editor.selection.isEmpty
    ? null
    : document.getText(editor.selection).trim() || null;

  // 공백·줄바꿈 없는 단일 식별자 감지 (함수명 선택 케이스)
  const isSingleIdentifier =
    rawSelection !== null &&
    !rawSelection.includes('\n') &&
    !rawSelection.includes(' ') &&
    /^[A-Za-z_$][A-Za-z0-9_$]*$/.test(rawSelection);

  const selection = isSingleIdentifier ? null : rawSelection;
  let focusFunctionName: string | null = isSingleIdentifier ? rawSelection : null;

  // 선택이 없고 함수명도 없으면 커서 위치의 심볼을 Document Symbol API 로 조회
  if (!selection && !focusFunctionName) {
    focusFunctionName = await getFunctionAtCursor(document, editor.selection.active);
  }

  return {
    filePath: document.fileName,
    fileName: path.basename(document.fileName),
    language: detectLanguage(ext),
    fullContent,
    selection,
    focusFunctionName,
    imports: extractImports(fullContent),
  };
}

/** 커서 위치에서 가장 가까운 함수/메서드 심볼 이름을 반환한다. */
async function getFunctionAtCursor(
  document: vscode.TextDocument,
  position: vscode.Position,
): Promise<string | null> {
  try {
    const symbols = await vscode.commands.executeCommand<vscode.DocumentSymbol[]>(
      'vscode.executeDocumentSymbolProvider',
      document.uri,
    );
    if (!symbols) return null;

    // 재귀적으로 커서를 포함하는 가장 안쪽 함수/메서드 심볼 탐색
    const FUNC_KINDS = new Set([
      vscode.SymbolKind.Function,
      vscode.SymbolKind.Method,
      vscode.SymbolKind.Constructor,
    ]);
    return findInnermostSymbol(symbols, position, FUNC_KINDS)?.name ?? null;
  } catch {
    return null;
  }
}

function findInnermostSymbol(
  symbols: vscode.DocumentSymbol[],
  position: vscode.Position,
  kinds: Set<vscode.SymbolKind>,
): vscode.DocumentSymbol | null {
  let best: vscode.DocumentSymbol | null = null;
  for (const sym of symbols) {
    if (!sym.range.contains(position)) continue;
    if (kinds.has(sym.kind)) best = sym;
    const child = findInnermostSymbol(sym.children, position, kinds);
    if (child) best = child;
  }
  return best;
}

function detectLanguage(ext: string): string {
  const map: Record<string, string> = {
    tsx: 'TypeScript (React)',
    ts:  'TypeScript',
    jsx: 'JavaScript (React)',
    js:  'JavaScript',
    vue: 'Vue',
  };
  return (map[ext] ?? (ext.toUpperCase() || 'Unknown'));
}

function extractImports(content: string): string[] {
  const matches = content.match(/^import\s+.+$/gm) ?? [];
  return matches.slice(0, 30).map(s => s.trim());
}
