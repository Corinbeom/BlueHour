import { apiFetch, type ApiResponse } from "@/lib/api";
import type { CultureFitQuestion, CultureFitSession } from "./types";

function unwrap<T>(res: ApiResponse<T>, fallback: string): T {
  if (!res.success || res.data === undefined) {
    throw new Error(res.error?.message ?? fallback);
  }
  return res.data;
}

export async function scrapeCultureFitUrl(url: string): Promise<string> {
  const res = await apiFetch<ApiResponse<{ extractedText: string }>>(
    "/api/culture-fit/scrape",
    {
      method: "POST",
      body: JSON.stringify({ url }),
    },
  );
  return unwrap(res, "URL 텍스트 추출에 실패했습니다.").extractedText;
}

export async function createCultureFitSession(input: {
  companyName?: string | null;
  companyCultureText: string;
  jobDescriptionText?: string | null;
  positionType?: string | null;
}): Promise<CultureFitSession> {
  const res = await apiFetch<ApiResponse<CultureFitSession>>(
    "/api/culture-fit/sessions",
    {
      method: "POST",
      body: JSON.stringify({
        companyName: input.companyName ?? null,
        companyCultureText: input.companyCultureText,
        jobDescriptionText: input.jobDescriptionText ?? null,
        positionType: input.positionType ?? null,
      }),
    },
  );
  return unwrap(res, "컬처핏 세션 생성에 실패했습니다.");
}

export async function listCultureFitSessions(): Promise<CultureFitSession[]> {
  const res = await apiFetch<ApiResponse<CultureFitSession[]>>(
    "/api/culture-fit/sessions",
  );
  return unwrap(res, "컬처핏 세션 목록 조회에 실패했습니다.");
}

export async function getCultureFitSession(sessionId: number): Promise<CultureFitSession> {
  const res = await apiFetch<ApiResponse<CultureFitSession>>(
    `/api/culture-fit/sessions/${sessionId}`,
  );
  return unwrap(res, "컬처핏 세션 조회에 실패했습니다.");
}

export async function submitCultureFitAnswer(input: {
  sessionId: number;
  questionId: number;
  answerText: string;
}): Promise<CultureFitQuestion> {
  const res = await apiFetch<ApiResponse<CultureFitQuestion>>(
    `/api/culture-fit/sessions/${input.sessionId}/questions/${input.questionId}/answer`,
    {
      method: "POST",
      body: JSON.stringify({ answerText: input.answerText }),
    },
  );
  return unwrap(res, "답변 제출에 실패했습니다.");
}

export async function generateCultureFitFeedback(input: {
  sessionId: number;
  questionId: number;
}): Promise<CultureFitQuestion> {
  const res = await apiFetch<ApiResponse<CultureFitQuestion>>(
    `/api/culture-fit/sessions/${input.sessionId}/questions/${input.questionId}/feedback`,
    { method: "POST" },
  );
  return unwrap(res, "피드백 생성에 실패했습니다.");
}

export async function completeCultureFitSession(sessionId: number): Promise<CultureFitSession> {
  const res = await apiFetch<ApiResponse<CultureFitSession>>(
    `/api/culture-fit/sessions/${sessionId}/complete`,
    { method: "PUT" },
  );
  return unwrap(res, "세션 완료 처리에 실패했습니다.");
}
