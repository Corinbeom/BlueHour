import { describe, expect, it } from "vitest";
import type { CoachSummary } from "@/features/coach/api/types";
import { diagnoseBottleneck, diagnosticAssistantTurn } from "../diagnostics";

function summary(overrides: Partial<CoachSummary> = {}): CoachSummary {
  return {
    targetRoles: ["백엔드 개발자"],
    inferredFrom: "TARGET_ROLES",
    roleCategory: "DEVELOPER",
    technicalTrack: true,
    recruitment: {
      totalApplications: 3,
      statusBreakdown: { INTERVIEWING: 1 },
      recentJdTitles: ["Backend Engineer"],
    },
    resume: {
      uploadedCount: 1,
      lastAnalyzedAt: null,
    },
    interview: {
      totalSessions: 1,
      completedSessions: 1,
      averageTurns: 4,
    },
    quiz: {
      totalAttempts: 0,
      topicAccuracy: {},
      topicAttempts: {},
    },
    ...overrides,
  };
}

describe("diagnoseBottleneck", () => {
  it("지원 5건 이상이고 서류 통과율이 10% 미만이면 RESUME을 우선 반환한다", () => {
    const result = diagnoseBottleneck(summary({
      recruitment: {
        totalApplications: 8,
        statusBreakdown: { APPLIED: 8 },
        recentJdTitles: ["Backend Engineer"],
      },
    }));

    expect(result.type).toBe("RESUME");
    expect(result.action).toContain("JD와 이력서");
  });

  it("면접 이후 단계가 3건 이상이고 합격 전환율이 낮으면 INTERVIEW를 반환한다", () => {
    const result = diagnoseBottleneck(summary({
      recruitment: {
        totalApplications: 6,
        statusBreakdown: { INTERVIEWING: 3, APPLIED: 3 },
        recentJdTitles: [],
      },
    }));

    expect(result.type).toBe("INTERVIEW");
    expect(result.action).toBe("AI 모의 면접 시작");
  });

  it("10회 이상 시도한 퀴즈 주제만 약점 후보로 판단한다", () => {
    const lowAttempts = diagnoseBottleneck(summary({
      recruitment: {
        totalApplications: 2,
        statusBreakdown: { INTERVIEWING: 1 },
        recentJdTitles: [],
      },
      quiz: {
        totalAttempts: 9,
        topicAccuracy: { OS: 0.2 },
        topicAttempts: { OS: 9 },
      },
    }));
    const enoughAttempts = diagnoseBottleneck(summary({
      recruitment: {
        totalApplications: 2,
        statusBreakdown: { INTERVIEWING: 1 },
        recentJdTitles: [],
      },
      quiz: {
        totalAttempts: 10,
        topicAccuracy: { OS: 0.2 },
        topicAttempts: { OS: 10 },
      },
    }));

    expect(lowAttempts.type).toBe("NONE");
    expect(enoughAttempts.type).toBe("QUIZ");
  });

  it("지원 데이터가 없으면 EMPTY를 반환한다", () => {
    const result = diagnoseBottleneck(summary({
      recruitment: {
        totalApplications: 0,
        statusBreakdown: {},
        recentJdTitles: [],
      },
    }));

    expect(result.type).toBe("EMPTY");
  });
});

describe("diagnosticAssistantTurn", () => {
  it("진단 결과를 첫 assistant turn 문장으로 변환한다", () => {
    const diagnostic = diagnoseBottleneck(summary({
      recruitment: {
        totalApplications: 0,
        statusBreakdown: {},
        recentJdTitles: [],
      },
    }));

    expect(diagnosticAssistantTurn(diagnostic)).toContain("추천 액션:");
  });
});
