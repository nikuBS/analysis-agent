import { spawn } from 'child_process';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';

const TIMEOUT_MS = 120_000;

const CANDIDATE_PATHS = [
  '/usr/local/bin/claude',
  '/opt/homebrew/bin/claude',
  path.join(os.homedir(), '.local/bin/claude'),
  path.join(os.homedir(), 'Library/Application Support/Claude/claude-code-vm/2.1.78/claude'),
  path.join(os.homedir(), 'Library/Application Support/Claude/claude-code/2.1.78/claude'),
  path.join(os.homedir(), 'Library/Application Support/Claude/claude-code-vm/2.1.72/claude'),
  path.join(os.homedir(), 'Library/Application Support/Claude/claude-code/2.1.72/claude'),
  path.join(os.homedir(), '.npm/bin/claude'),
  path.join(os.homedir(), '.nvm/bin/claude'),
];

export class LlmException extends Error {
  constructor(message: string, public readonly cause?: Error) {
    super(message);
    this.name = 'LlmException';
  }
}

export class CliLlmClient {
  constructor(
    private readonly binaryPath: string,
    private readonly model?: string,
  ) {}

  /**
   * Claude CLI 를 subprocess 로 실행하고 응답을 반환한다.
   * onChunk 가 제공되면 실시간 스트리밍 콜백을 호출한다.
   */
  complete(prompt: string, onChunk?: (accumulated: string) => void): Promise<string> {
    return new Promise((resolve, reject) => {
      const args = ['--print'];
      if (this.model) { args.push('--model', this.model); }

      let proc: ReturnType<typeof spawn>;
      try {
        proc = spawn(this.binaryPath, args, { stdio: ['pipe', 'pipe', 'pipe'] });
      } catch (err: unknown) {
        return reject(new LlmException(
          `Claude CLI 실행 실패: ${(err as Error).message}\n` +
          '› claude CLI가 설치되어 있는지 확인해주세요.',
          err as Error,
        ));
      }

      // stdin 으로 프롬프트 전달 후 EOF
      proc.stdin!.write(prompt, 'utf8');
      proc.stdin!.end();

      const chunks: string[] = [];
      let accumulated = '';

      proc.stdout!.on('data', (data: Buffer) => {
        const text = data.toString('utf8');
        chunks.push(text);
        accumulated += text;
        onChunk?.(accumulated);
      });

      const timer = setTimeout(() => {
        proc.kill('SIGKILL');
        if (accumulated.trim()) {
          onChunk?.(`${accumulated}\n\n⚠️ 응답 시간 초과로 부분 결과만 표시됩니다.`);
          resolve(accumulated.trim());
        } else {
          reject(new LlmException(`Claude CLI 응답 시간 초과 (${TIMEOUT_MS / 1000}초)\n› 네트워크 상태나 claude 로그인 여부를 확인해주세요.`));
        }
      }, TIMEOUT_MS);

      proc.on('close', (code) => {
        clearTimeout(timer);
        const output = accumulated.trim();

        try {
          checkNotLoggedIn(output);
        } catch (e) {
          return reject(e);
        }

        if (code !== 0 && !output) {
          return reject(new LlmException(`Claude CLI 오류 (exit ${code}). Settings에서 버전 확인 버튼으로 상태를 확인해주세요.`));
        }

        if (!output) {
          return reject(new LlmException('Claude CLI 응답이 비어있습니다.'));
        }

        resolve(output);
      });

      proc.on('error', (err) => {
        clearTimeout(timer);
        reject(new LlmException(`Claude CLI 실행 실패: ${err.message}`, err));
      });
    });
  }

  // ── Static helpers ──────────────────────────────────────────────

  static findBinary(): string | null {
    for (const p of CANDIDATE_PATHS) {
      try {
        fs.accessSync(p, fs.constants.X_OK);
        return p;
      } catch { /* not found */ }
    }
    // which claude
    try {
      const { execSync } = require('child_process') as typeof import('child_process');
      const result = execSync('which claude', { encoding: 'utf8', timeout: 5000 }).trim();
      if (result) {
        try { fs.accessSync(result, fs.constants.X_OK); return result; } catch { /* skip */ }
      }
    } catch { /* skip */ }
    return null;
  }

  static checkVersion(binaryPath: string): string | null {
    try {
      const { execSync } = require('child_process') as typeof import('child_process');
      return execSync(`"${binaryPath}" --version`, { encoding: 'utf8', timeout: 5000 }).trim() || null;
    } catch {
      return null;
    }
  }

  static checkLoginStatus(): string {
    try {
      const claudeJson = path.join(os.homedir(), '.claude.json');
      if (!fs.existsSync(claudeJson)) {
        return '❌ 로그인되어 있지 않습니다.';
      }
      const content = fs.readFileSync(claudeJson, 'utf8');
      const ok = content.includes('"oauthAccount"') &&
                 content.includes('"accountUuid"') &&
                 content.includes('"emailAddress"');
      return ok ? '✅ 로그인 상태 정상' : '❌ 로그인되어 있지 않습니다.';
    } catch (e: unknown) {
      return `❌ 확인 실패: ${(e as Error).message}`;
    }
  }
}

function checkNotLoggedIn(output: string): void {
  const outputSignal =
    /not logged in/i.test(output) ||
    /please run \/login/i.test(output) ||
    /not authenticated/i.test(output);

  let fileSignal = false;
  try {
    const claudeJson = path.join(os.homedir(), '.claude.json');
    if (!fs.existsSync(claudeJson)) {
      fileSignal = true;
    } else {
      const c = fs.readFileSync(claudeJson, 'utf8');
      fileSignal = !c.includes('"oauthAccount"') || !c.includes('"accountUuid"');
    }
  } catch { /* ignore */ }

  if (outputSignal || fileSignal) {
    throw new LlmException(
      '로그인이 필요합니다.\n' +
      '› 터미널에서 claude 를 실행하고 /login 명령을 입력해주세요.',
    );
  }
}
