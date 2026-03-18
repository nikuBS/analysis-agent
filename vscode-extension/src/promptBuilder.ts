import { FileContext } from './contextCollector';

const MAX_CONTENT_LENGTH = 8_000;

/** WebStorm 플러그인의 PromptBuilder 를 TypeScript 로 직접 포팅 */
export const PromptBuilder = {

  build(context: FileContext): string {
    const analysisTarget = context.selection
      ? selectedSection(context.selection)
      : fullFileSection(context.fullContent);

    const importsSection = context.imports.length > 0
      ? importsBlock(context.imports)
      : '';

    const focusInstruction = context.focusFunctionName
      ? `⚠️ 분석 집중 대상: \`${context.focusFunctionName}\` 함수(또는 변수/컴포넌트)에 집중하여 분석해주세요. 전체 파일 코드에서 해당 심볼의 정의와 호출 흐름을 추적하세요.\n`
      : '';

    return `당신은 프론트엔드 시니어 개발자이자 코드 분석 전문가입니다.
아래 코드를 분석하여 업무 프로세스와 코드 동작 흐름을 구조화해서 설명해주세요.

${focusInstruction}분석 대상 파일 정보:
- 파일명: ${context.fileName}
- 언어: ${context.language}
- 파일 경로: ${context.filePath}

${importsSection}
${analysisTarget}

다음 형식으로 분석 결과를 작성해주세요. 각 항목은 한국어로 작성합니다.

## 1. 기능 요약
이 코드가 어떤 역할을 하는지 2~3줄로 요약하세요.

## 2. 주요 파일 / 컴포넌트
관련된 주요 컴포넌트나 파일을 나열하고 각 역할을 설명하세요.

## 3. 화면 동작 흐름
사용자 인터랙션부터 렌더링까지의 흐름을 단계별로 설명하세요.
(예: 버튼 클릭 → 핸들러 호출 → 상태 변경 → 리렌더링)

## 4. 상태 / 데이터 흐름
useState, useReducer, Context, Redux 등의 상태 관리 흐름을 설명하세요.
props 전달 구조도 포함하세요.

## 5. API 연계
fetch, axios, react-query, SWR 등 API 호출 지점과 엔드포인트를 정리하세요.
없으면 "없음"으로 표기하세요.

## 6. 예외 / 리스크
에러 처리, 엣지 케이스, 잠재적 버그 가능성을 나열하세요.

## 7. 확인 필요 사항
코드를 완전히 이해하기 위해 추가로 확인해야 할 파일이나 로직을 나열하세요.`;
  },

  buildCustom(context: FileContext, userPrompt: string): string {
    const codeSection = context.selection
      ? selectedSection(context.selection)
      : fullFileSection(context.fullContent);

    const importsSection = context.imports.length > 0
      ? importsBlock(context.imports)
      : '';

    const focusNote = context.focusFunctionName
      ? `특히 \`${context.focusFunctionName}\` 함수를 중심으로 답변해주세요.\n\n`
      : '';

    return `당신은 프론트엔드 시니어 개발자이자 코드 분석 전문가입니다.
아래 코드를 분석하여 두 가지를 한국어로 답변해주세요.

분석 대상 파일 정보:
- 파일명: ${context.fileName}
- 언어: ${context.language}
- 파일 경로: ${context.filePath}

${importsSection}
${codeSection}

${focusNote}---

다음 형식으로 표준 분석을 먼저 수행한 후, 추가 질문에도 답변하세요.

## 1. 기능 요약
이 코드가 어떤 역할을 하는지 2~3줄로 요약하세요.

## 2. 주요 파일 / 컴포넌트
관련된 주요 컴포넌트나 파일을 나열하고 각 역할을 설명하세요.

## 3. 화면 동작 흐름
사용자 인터랙션부터 렌더링까지의 흐름을 단계별로 설명하세요.

## 4. 상태 / 데이터 흐름
useState, useReducer, Context, Redux 등의 상태 관리 흐름을 설명하세요.

## 5. API 연계
fetch, axios, react-query, SWR 등 API 호출 지점과 엔드포인트를 정리하세요.
없으면 "없음"으로 표기하세요.

## 6. 예외 / 리스크
에러 처리, 엣지 케이스, 잠재적 버그 가능성을 나열하세요.

## 7. 확인 필요 사항
코드를 완전히 이해하기 위해 추가로 확인해야 할 파일이나 로직을 나열하세요.

---

## 💬 추가 질문 답변
${userPrompt}`;
  },
};

function fullFileSection(content: string): string {
  const truncated = content.slice(0, MAX_CONTENT_LENGTH);
  const note = content.length > MAX_CONTENT_LENGTH ? '\n[주의: 파일이 길어 일부만 포함됨]' : '';
  return `분석할 전체 파일 코드:\n\`\`\`\n${truncated}${note}\n\`\`\``;
}

function selectedSection(selection: string): string {
  return `분석할 선택 코드:\n\`\`\`\n${selection}\n\`\`\``;
}

function importsBlock(imports: string[]): string {
  return `파일의 Import 목록:\n\`\`\`\n${imports.join('\n')}\n\`\`\`\n`;
}
