import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  createCultureFitSession,
  generateCultureFitFeedback,
  scrapeCultureFitUrl,
  submitCultureFitAnswer,
} from "../cultureFitApi";

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

beforeEach(() => {
  mockFetch.mockReset();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("cultureFitApi", () => {
  it("scrapeCultureFitUrl: URL 스크랩 엔드포인트로 요청한다", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ success: true, data: { extractedText: "회사 문화" } }),
    });

    const result = await scrapeCultureFitUrl("https://example.com/culture");

    expect(result).toBe("회사 문화");
    const [, init] = mockFetch.mock.calls[0];
    expect(JSON.parse(init.body)).toEqual({ url: "https://example.com/culture" });
  });

  it("createCultureFitSession: 회사 문화와 JD를 body에 포함한다", async () => {
    const session = { id: 1, questions: [] };
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ success: true, data: session }),
    });

    const result = await createCultureFitSession({
      companyName: "테스트 회사",
      companyCultureText: "기업 문화 텍스트",
      jobDescriptionText: "JD",
      positionType: "PM",
    });

    expect(result).toEqual(session);
    const [, init] = mockFetch.mock.calls[0];
    expect(mockFetch.mock.calls[0][0]).toContain("/api/culture-fit/sessions");
    expect(JSON.parse(init.body)).toEqual({
      companyName: "테스트 회사",
      companyCultureText: "기업 문화 텍스트",
      jobDescriptionText: "JD",
      positionType: "PM",
    });
  });

  it("submitCultureFitAnswer: 질문 답변 저장 경로로 요청한다", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ success: true, data: { id: 10, answerText: "답변" } }),
    });

    await submitCultureFitAnswer({
      sessionId: 1,
      questionId: 10,
      answerText: "답변",
    });

    expect(mockFetch.mock.calls[0][0]).toContain(
      "/api/culture-fit/sessions/1/questions/10/answer",
    );
  });

  it("generateCultureFitFeedback: 피드백 생성 경로로 요청한다", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ success: true, data: { id: 10, feedbackStatus: "COMPLETED" } }),
    });

    await generateCultureFitFeedback({ sessionId: 1, questionId: 10 });

    expect(mockFetch.mock.calls[0][0]).toContain(
      "/api/culture-fit/sessions/1/questions/10/feedback",
    );
  });
});
