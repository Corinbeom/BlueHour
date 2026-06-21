import { cleanup, render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CoachDashboardView } from "../CoachDashboardView";
import type { CoachAnalysis, CoachSummary } from "../../api/types";

const mocks = vi.hoisted(() => ({
  summary: vi.fn(),
  analysis: vi.fn(),
  refresh: vi.fn(),
}));

vi.mock("../../hooks/useCoach", () => ({
  useCoachSummary: () => mocks.summary(),
  useCoachAnalysis: () => mocks.analysis(),
  useRefreshCoachAnalysis: () => mocks.refresh(),
}));

vi.mock("recharts", () => ({
  PolarAngleAxis: () => null,
  PolarGrid: () => null,
  Radar: () => null,
  RadarChart: ({ children }: { children?: ReactNode }) => <div>{children}</div>,
  ResponsiveContainer: ({ children }: { children?: ReactNode }) => <div>{children}</div>,
  Tooltip: () => null,
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function summary(overrides: Partial<CoachSummary> = {}): CoachSummary {
  return {
    targetRoles: ["백엔드 개발자"],
    inferredFrom: "TARGET_ROLES",
    roleCategory: "DEVELOPER",
    technicalTrack: true,
    recruitment: {
      totalApplications: 1,
      statusBreakdown: { APPLIED: 1 },
      recentJdTitles: ["백엔드 개발자"],
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
      totalAttempts: 10,
      topicAccuracy: { OS: 0.4 },
      topicAttempts: { OS: 10 },
    },
    ...overrides,
  };
}

const analysis: CoachAnalysis = {
  score: 70,
  primary: "백엔드 개발자",
  strengths: ["지원 시작"],
  gaps: ["면접 부족"],
  plan: [{ d: 1, do: "이력서 보강" }],
  today: "이력서 보강",
  needsTargetRoles: false,
};

function mockCoach(data: CoachSummary) {
  mocks.summary.mockReturnValue({ data, isLoading: false, isError: false });
  mocks.analysis.mockReturnValue({ data: analysis, isLoading: false, isError: false });
  mocks.refresh.mockReturnValue({ mutate: vi.fn(), isPending: false });
}

describe("CoachDashboardView", () => {
  it("개발 직무는 퀴즈 CTA와 취약도 패널을 보여준다", () => {
    mockCoach(summary());

    render(<CoachDashboardView />);

    expect(screen.getByRole("link", { name: /퀴즈 풀기/ })).toBeInTheDocument();
    expect(screen.getByText("퀴즈 취약도")).toBeInTheDocument();
  });

  it("비개발 직무는 기본 화면에서 퀴즈 CTA와 취약도 패널을 숨긴다", () => {
    mockCoach(summary({
      targetRoles: ["UX/UI 디자이너"],
      roleCategory: "DESIGN",
      technicalTrack: false,
    }));

    render(<CoachDashboardView />);

    expect(screen.queryByRole("link", { name: /퀴즈 풀기/ })).not.toBeInTheDocument();
    expect(screen.queryByText("퀴즈 취약도")).not.toBeInTheDocument();
    expect(screen.getByText("경험 정리")).toBeInTheDocument();
  });
});
