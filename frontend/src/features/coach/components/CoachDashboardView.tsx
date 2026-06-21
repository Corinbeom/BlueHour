"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import {
  PolarAngleAxis,
  PolarGrid,
  Radar,
  RadarChart,
  ResponsiveContainer,
  Tooltip,
} from "recharts";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useCoachAnalysis, useCoachSummary, useRefreshCoachAnalysis } from "../hooks/useCoach";
import type { CoachAnalysis, CoachSummary } from "../api/types";

const topicLabels: Record<string, string> = {
  OS: "운영체제",
  NETWORK: "네트워크",
  DB: "데이터베이스",
  SPRING: "Spring",
  JAVA: "Java",
  DATA_STRUCTURE: "자료구조",
  ALGORITHM: "알고리즘",
  ARCHITECTURE: "아키텍처",
  CLOUD: "클라우드",
};

function percent(value: number) {
  return Math.round(value * 100);
}

function stepLabel(step: string) {
  const map: Record<string, string> = {
    READY: "준비",
    APPLIED: "지원",
    DOC_PASSED: "서류",
    TEST_PHASE: "테스트",
    INTERVIEWING: "면접",
    OFFERED: "합격",
    REJECTED: "불합격",
  };
  return map[step] ?? step;
}

function readinessData(summary?: CoachSummary, analysis?: CoachAnalysis) {
  const technicalTrack = summary?.technicalTrack ?? false;
  const applications = Math.min(100, (summary?.recruitment.totalApplications ?? 0) * 12);
  const resume = Math.min(100, (summary?.resume.uploadedCount ?? 0) * 40);
  const interviewTotal = summary?.interview.totalSessions ?? 0;
  const interviewCompleted = summary?.interview.completedSessions ?? 0;
  const interview = interviewTotal > 0 ? Math.round((interviewCompleted / interviewTotal) * 100) : 0;
  const quizValues = Object.values(summary?.quiz.topicAccuracy ?? {});
  const quiz = quizValues.length
    ? Math.round((quizValues.reduce((sum, value) => sum + value, 0) / quizValues.length) * 100)
    : 0;
  const jd = analysis?.score ?? 0;

  return [
    { axis: "지원 활동", value: applications },
    { axis: "이력서", value: resume },
    { axis: "면접 연습", value: interview },
    { axis: technicalTrack ? "기술 학습" : "경험 정리", value: technicalTrack ? quiz : resume },
    { axis: "직무 적합", value: jd },
  ];
}

function useTypingText(text?: string) {
  const [typing, setTyping] = useState({ source: "", count: 0 });

  useEffect(() => {
    if (!text) return;
    let i = 0;
    const timer = window.setInterval(() => {
      i += 1;
      setTyping({ source: text, count: i });
      if (i >= text.length) window.clearInterval(timer);
    }, 30);
    return () => window.clearInterval(timer);
  }, [text]);

  if (!text || typing.source !== text) return "";
  return text.slice(0, typing.count);
}

export function CoachDashboardView() {
  const summary = useCoachSummary();
  const analysis = useCoachAnalysis();
  const refresh = useRefreshCoachAnalysis();

  const summaryData = summary.data;
  const analysisData = analysis.data;
  const typedToday = useTypingText(analysisData?.today);
  const technicalTrack = summaryData?.technicalTrack ?? false;
  const chartData = useMemo(
    () => readinessData(summaryData, analysisData),
    [summaryData, analysisData],
  );
  const hasAnyData = !!summaryData && (
    summaryData.recruitment.totalApplications > 0 ||
    summaryData.resume.uploadedCount > 0 ||
    summaryData.interview.totalSessions > 0 ||
    (technicalTrack && summaryData.quiz.totalAttempts > 0)
  );

  if (summary.isLoading || analysis.isLoading) {
    return <CoachSkeleton />;
  }

  if (summary.isError || analysis.isError) {
    return (
      <section className="rounded-xl border border-destructive/40 bg-destructive/5 p-5">
        <p className="text-sm font-semibold text-destructive">코치 데이터를 불러오지 못했습니다.</p>
        <p className="mt-1 text-sm text-muted-foreground">잠시 후 다시 시도해 주세요.</p>
      </section>
    );
  }

  return (
    <div className="space-y-5">
      {analysisData?.needsTargetRoles && <TargetRoleBanner />}

      <header className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <div className="mb-2 flex flex-wrap gap-1.5">
            {(summaryData?.targetRoles ?? []).map((role) => (
              <span
                key={role}
                className="rounded-md border border-border bg-card px-2 py-1 text-xs font-medium text-foreground"
              >
                {role}
              </span>
            ))}
          </div>
          <h1 className="text-xl font-bold tracking-[-0.02em] text-foreground">
            AI 코치 대시보드
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">
            지원 현황, 이력서, 면접 연습 기록을 기준으로 오늘의 우선순위를 계산합니다.
            {technicalTrack ? " 개발 직무는 CS 학습 기록도 함께 반영합니다." : ""}
          </p>
        </div>
        <button
          type="button"
          onClick={() => refresh.mutate()}
          disabled={refresh.isPending}
          className={cn(buttonVariants({ variant: "outline" }), "gap-1.5 self-start lg:self-auto")}
        >
          <span className="material-symbols-outlined text-base">refresh</span>
          새로고침
        </button>
      </header>

      <section className="grid gap-4 xl:grid-cols-[360px_minmax(0,1fr)]">
        <ReadinessCard analysis={analysisData} typedToday={typedToday} />
        <div className="grid gap-4 lg:grid-cols-2">
          <RadarPanel data={chartData} />
          <TodayActionCard today={analysisData?.today} technicalTrack={technicalTrack} />
        </div>
      </section>

      {!hasAnyData && <EmptyCoachState needsTargetRoles={!!analysisData?.needsTargetRoles} technicalTrack={technicalTrack} />}

      <section className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
        <div className="grid gap-4 lg:grid-cols-2">
          {technicalTrack ? (
            <WeaknessHeatmap topicAccuracy={summaryData?.quiz.topicAccuracy ?? {}} />
          ) : (
            <ExperiencePrepPanel />
          )}
          <WeeklyPlanTimeline plan={analysisData?.plan ?? []} technicalTrack={technicalTrack} />
        </div>
        <SummaryPanel summary={summaryData} analysis={analysisData} technicalTrack={technicalTrack} />
      </section>
    </div>
  );
}

function ReadinessCard({
  analysis,
  typedToday,
}: {
  analysis?: CoachAnalysis;
  typedToday: string;
}) {
  const score = analysis?.score ?? 0;
  return (
    <section className="rounded-xl border border-border bg-card p-5">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-xs font-semibold text-muted-foreground">준비도 점수</p>
          <p className="mt-1 text-sm font-medium text-foreground">
            {analysis?.primary || "직무 설정 필요"}
          </p>
        </div>
        <div
          className="flex size-24 items-center justify-center rounded-full"
          style={{
            background: `conic-gradient(var(--color-teal) ${score * 3.6}deg, var(--color-muted) 0deg)`,
          }}
        >
          <div className="flex size-20 flex-col items-center justify-center rounded-full bg-card">
            <span className="text-2xl font-bold text-foreground">{score}</span>
            <span className="text-[10px] text-muted-foreground">/ 100</span>
          </div>
        </div>
      </div>

      <div className="mt-5 rounded-lg border border-border bg-muted/40 p-4">
        <p className="text-xs font-semibold text-muted-foreground">오늘 할 일</p>
        <p className="mt-2 min-h-6 text-base font-semibold text-foreground">
          {typedToday}
          {typedToday && <span className="ml-0.5 animate-pulse text-primary">|</span>}
        </p>
      </div>

      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        <InsightList title="강점" items={analysis?.strengths ?? []} tone="good" />
        <InsightList title="보완" items={analysis?.gaps ?? []} tone="warn" />
      </div>
    </section>
  );
}

function InsightList({
  title,
  items,
  tone,
}: {
  title: string;
  items: string[];
  tone: "good" | "warn";
}) {
  return (
    <div className="rounded-lg border border-border p-3">
      <p className="mb-2 text-xs font-semibold text-muted-foreground">{title}</p>
      <div className="space-y-1.5">
        {(items.length ? items : ["분석 대기"]).map((item) => (
          <div key={item} className="flex items-center gap-1.5 text-sm text-foreground">
            <span
              className={cn(
                "size-1.5 rounded-full",
                tone === "good" ? "bg-teal" : "bg-chart-3",
              )}
            />
            <span className="truncate">{item}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function RadarPanel({ data }: { data: Array<{ axis: string; value: number }> }) {
  return (
    <section className="rounded-xl border border-border bg-card p-5">
      <div className="mb-4">
        <p className="text-sm font-semibold text-foreground">역량 레이더</p>
        <p className="mt-1 text-xs text-muted-foreground">5개 준비 축을 100점 기준으로 환산</p>
      </div>
      <div className="h-[260px]">
        <ResponsiveContainer width="100%" height="100%">
          <RadarChart data={data} outerRadius="72%">
            <PolarGrid stroke="var(--color-border)" />
            <PolarAngleAxis dataKey="axis" tick={{ fontSize: 11, fill: "var(--color-muted-foreground)" }} />
            <Tooltip content={<RadarTooltip />} />
            <Radar
              dataKey="value"
              stroke="var(--color-primary)"
              fill="var(--color-teal)"
              fillOpacity={0.28}
            />
          </RadarChart>
        </ResponsiveContainer>
      </div>
    </section>
  );
}

function RadarTooltip({
  active,
  payload,
}: {
  active?: boolean;
  payload?: Array<{ payload: { axis: string; value: number } }>;
}) {
  if (!active || !payload?.length) return null;
  const item = payload[0].payload;
  return (
    <div className="rounded-lg border border-border bg-popover px-3 py-2 text-xs shadow-md">
      <p className="font-semibold text-foreground">{item.axis}</p>
      <p className="text-muted-foreground">{item.value}점</p>
    </div>
  );
}

function TodayActionCard({ today, technicalTrack }: { today?: string; technicalTrack: boolean }) {
  const actions = technicalTrack
    ? [
        { icon: "quiz", label: "퀴즈 풀기", href: "/study-quiz", primary: true },
        { icon: "record_voice_over", label: "면접 연습", href: "/speech-interview" },
        { icon: "description", label: "이력서 분석", href: "/resume-analyzer" },
      ]
    : [
        { icon: "description", label: "이력서 분석", href: "/resume-analyzer", primary: true },
        { icon: "add_circle", label: "지원 현황 추가", href: "/application-tracker" },
        { icon: "record_voice_over", label: "면접 연습", href: "/speech-interview" },
      ];

  return (
    <section className="rounded-xl border border-border bg-card p-5">
      <p className="text-sm font-semibold text-foreground">액션 바로가기</p>
      <p className="mt-2 min-h-10 text-sm text-muted-foreground">
        {today || "관심 직무와 활동 데이터가 쌓이면 오늘 할 일이 표시됩니다."}
      </p>
      <div className="mt-5 grid gap-2">
        {actions.map((action) => (
          <Link
            key={action.href}
            href={action.href}
            className={cn(
              buttonVariants({ variant: action.primary ? "default" : "outline" }),
              "justify-start gap-2",
            )}
          >
            <span className="material-symbols-outlined text-base">{action.icon}</span>
            {action.label}
          </Link>
        ))}
      </div>
    </section>
  );
}

function WeaknessHeatmap({ topicAccuracy }: { topicAccuracy: Record<string, number> }) {
  const rows: Array<[string, number]> = Object.entries(topicAccuracy).sort((a, b) => a[1] - b[1]);
  const displayRows: Array<[string, number]> = rows.length ? rows : [["EMPTY", 0]];
  return (
    <section className="rounded-xl border border-border bg-card p-5">
      <p className="text-sm font-semibold text-foreground">퀴즈 취약도</p>
      <div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-3">
        {displayRows.map(([topic, value]) => (
          <div
            key={topic}
            className="rounded-lg border border-border p-3"
            style={{
              background:
                topic === "EMPTY"
                  ? "var(--color-muted)"
                  : `color-mix(in oklab, ${value < 0.5 ? "var(--color-destructive)" : value < 0.75 ? "var(--color-chart-3)" : "var(--color-teal)"} 14%, transparent)`,
            }}
          >
            <p className="truncate text-xs font-medium text-muted-foreground">
              {topic === "EMPTY" ? "퀴즈 기록 없음" : topicLabels[topic] ?? topic}
            </p>
            <p className="mt-1 text-lg font-bold text-foreground">
              {topic === "EMPTY" ? "-" : `${percent(value)}%`}
            </p>
          </div>
        ))}
      </div>
    </section>
  );
}

function ExperiencePrepPanel() {
  const items = [
    { icon: "description", label: "이력서 핵심 경험", href: "/resume-analyzer" },
    { icon: "folder_special", label: "포트폴리오/성과 정리", href: "/resume-analyzer" },
    { icon: "work_history", label: "지원 직무 키워드", href: "/application-tracker" },
  ];
  return (
    <section className="rounded-xl border border-border bg-card p-5">
      <p className="text-sm font-semibold text-foreground">경험 정리</p>
      <p className="mt-1 text-xs text-muted-foreground">
        비개발 직무는 이력서, 포트폴리오, 지원 직무 키워드의 연결도를 우선 봅니다.
      </p>
      <div className="mt-4 grid gap-2">
        {items.map((item) => (
          <Link
            key={item.label}
            href={item.href}
            className="flex items-center gap-2.5 rounded-lg border border-border p-3 text-sm font-medium text-foreground transition-colors hover:bg-accent/50"
          >
            <span className="material-symbols-outlined text-base text-muted-foreground">{item.icon}</span>
            {item.label}
          </Link>
        ))}
      </div>
    </section>
  );
}

function WeeklyPlanTimeline({ plan, technicalTrack }: { plan: Array<{ d: number; do: string }>; technicalTrack: boolean }) {
  const rows = plan.length
    ? plan
    : technicalTrack
    ? [
        { d: 1, do: "관심 직무 설정" },
        { d: 2, do: "이력서 업로드" },
        { d: 3, do: "퀴즈 1회 완료" },
      ]
    : [
        { d: 1, do: "관심 직무 설정" },
        { d: 2, do: "이력서 업로드" },
        { d: 3, do: "지원 현황 추가" },
      ];
  return (
    <section className="rounded-xl border border-border bg-card p-5">
      <p className="text-sm font-semibold text-foreground">3일 실행 계획</p>
      <div className="mt-4 space-y-3">
        {rows.map((item) => (
          <div key={`${item.d}-${item.do}`} className="flex gap-3">
            <div className="flex size-8 flex-shrink-0 items-center justify-center rounded-lg bg-primary text-xs font-bold text-primary-foreground">
              D{item.d}
            </div>
            <div className="min-w-0 flex-1 rounded-lg border border-border px-3 py-2">
              <p className="truncate text-sm font-medium text-foreground">{item.do}</p>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

function SummaryPanel({
  summary,
  analysis,
  technicalTrack,
}: {
  summary?: CoachSummary;
  analysis?: CoachAnalysis;
  technicalTrack: boolean;
}) {
  const statusRows: Array<[string, number]> = Object.entries(summary?.recruitment.statusBreakdown ?? {});
  const displayStatusRows: Array<[string, number]> = statusRows.length ? statusRows : [["READY", 0]];
  return (
    <section className="rounded-xl border border-border bg-card p-5">
      <p className="text-sm font-semibold text-foreground">활동 요약</p>
      <div className="mt-4 grid grid-cols-2 gap-2">
        <Metric label="지원" value={summary?.recruitment.totalApplications ?? 0} />
        <Metric label="이력서" value={summary?.resume.uploadedCount ?? 0} />
        <Metric label="면접 완료" value={summary?.interview.completedSessions ?? 0} />
        {technicalTrack ? (
          <Metric label="퀴즈 풀이" value={summary?.quiz.totalAttempts ?? 0} />
        ) : (
          <Metric label="최근 공고" value={summary?.recruitment.recentJdTitles.length ?? 0} />
        )}
      </div>
      <div className="mt-4 space-y-2">
        {displayStatusRows.map(([step, count]) => (
          <div key={step} className="flex items-center justify-between text-sm">
            <span className="text-muted-foreground">{stepLabel(step)}</span>
            <span className="font-semibold text-foreground">{count}</span>
          </div>
        ))}
      </div>
      {analysis?.primary && (
        <p className="mt-4 rounded-lg bg-muted px-3 py-2 text-xs text-muted-foreground">
          분석 기준: <span className="font-semibold text-foreground">{analysis.primary}</span>
        </p>
      )}
    </section>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg border border-border p-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-1 text-xl font-bold text-foreground">{value}</p>
    </div>
  );
}

function EmptyCoachState({ needsTargetRoles, technicalTrack }: { needsTargetRoles: boolean; technicalTrack: boolean }) {
  const items = technicalTrack
    ? [
        { label: "관심 직무 설정", href: "/profile", done: !needsTargetRoles },
        { label: "이력서 업로드", href: "/profile", done: false },
        { label: "공고 지원 등록", href: "/application-tracker", done: false },
        { label: "CS 퀴즈 1회", href: "/study-quiz", done: false },
        { label: "면접 연습 1회", href: "/speech-interview", done: false },
      ]
    : [
        { label: "관심 직무 설정", href: "/profile", done: !needsTargetRoles },
        { label: "이력서 업로드", href: "/profile", done: false },
        { label: "지원 현황 추가", href: "/application-tracker", done: false },
        { label: "경험 정리", href: "/resume-analyzer", done: false },
        { label: "면접 연습 1회", href: "/speech-interview", done: false },
      ];
  return (
    <section className="rounded-xl border border-border bg-card p-5">
      <p className="text-sm font-semibold text-foreground">코치 분석을 시작하기 위한 체크리스트</p>
      <div className="mt-4 grid gap-2 md:grid-cols-5">
        {items.map((item) => (
          <Link
            key={item.label}
            href={item.href}
            className="flex min-h-20 flex-col justify-between rounded-lg border border-border p-3 transition-colors hover:bg-accent/50"
          >
            <span className="text-sm font-medium text-foreground">{item.label}</span>
            <span className={cn("material-symbols-outlined text-lg", item.done ? "text-teal" : "text-muted-foreground")}>
              {item.done ? "check_circle" : "arrow_forward"}
            </span>
          </Link>
        ))}
      </div>
    </section>
  );
}

function TargetRoleBanner() {
  return (
    <section className="flex flex-col gap-3 rounded-xl border border-chart-3/40 bg-chart-3/10 p-4 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <p className="text-sm font-semibold text-foreground">관심 직무가 필요합니다.</p>
        <p className="mt-1 text-sm text-muted-foreground">직무를 설정하면 AI 분석이 바로 활성화됩니다.</p>
      </div>
      <Link href="/profile" className={cn(buttonVariants({ variant: "outline" }), "self-start")}>
        직무 설정
      </Link>
    </section>
  );
}

function CoachSkeleton() {
  return (
    <div className="space-y-4">
      <div className="h-24 animate-pulse rounded-xl bg-muted" />
      <div className="grid gap-4 xl:grid-cols-3">
        <div className="h-72 animate-pulse rounded-xl bg-muted" />
        <div className="h-72 animate-pulse rounded-xl bg-muted xl:col-span-2" />
      </div>
      <div className="h-64 animate-pulse rounded-xl bg-muted" />
    </div>
  );
}
