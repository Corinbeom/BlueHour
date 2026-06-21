"use client";

import { useState } from "react";
import { usePathname } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/features/auth/hooks/useAuth";
import { isTechnicalTrack } from "@/features/member/roleCategory";
import { useRecruitmentEntries } from "@/features/application-tracker/hooks/useRecruitmentEntries";
import { useCsQuizSessions } from "@/features/study-quiz/hooks/useCsQuizSessions";
import { useResumeSessions } from "@/features/resume-analyzer/hooks/useResumeSessions";
import { cn } from "@/lib/utils";

// Hide panel on full-screen session pages
const SESSION_PATHS = ["/resume-analyzer/practice", "/study-quiz/practice"];

// Gradient start has no direct token (intentional mid-purple for hero card)
const C = {
  primary: "oklch(0.385 0.175 280)",
  green: "var(--color-teal)",
  amber: "var(--color-chart-3)",
  violet: "var(--color-chart-4)",
};

function stepColor(step: string) {
  switch (step) {
    case "INTERVIEWING": return C.amber;
    case "OFFERED": return C.green;
    case "APPLIED": return C.primary;
    default: return C.violet;
  }
}

function stepLabel(step: string) {
  switch (step) {
    case "READY": return "준비";
    case "APPLIED": return "지원";
    case "DOC_PASSED": return "서류 통과";
    case "TEST_PHASE": return "코딩테스트";
    case "INTERVIEWING": return "면접 진행";
    case "OFFERED": return "최종 합격";
    case "REJECTED": return "불합격";
    default: return step;
  }
}

function FocusPanel() {
  const { user } = useAuth();
  const { data: resumeSessions = [] } = useResumeSessions();
  const { data: entries = [] } = useRecruitmentEntries();

  const displayName = user?.displayName ?? user?.email?.split("@")[0] ?? "사용자";
  void displayName;

  const incomplete = resumeSessions.find(
    (s) => s.status === "QUESTIONS_READY" && s.answeredQuestionCount < s.totalQuestionCount
  );
  const focusHref = incomplete
    ? `/resume-analyzer/practice?sessionId=${incomplete.id}`
    : "/resume-analyzer";
  const focusTitle = incomplete
    ? `${incomplete.title}`
    : resumeSessions.length === 0
    ? "이력서를 업로드하고 AI 면접 준비를 시작하세요"
    : "새 면접 세션 시작하기";
  const focusDesc = incomplete
    ? `남은 질문 ${incomplete.totalQuestionCount - incomplete.answeredQuestionCount}개`
    : "이력서/포트폴리오 분석 → 맞춤 질문 생성";

  const upcomingInterviews = entries.filter((e) => e.step === "INTERVIEWING").slice(0, 2);
  const technicalTrack = isTechnicalTrack(user?.targetRoles);
  const quickActions = [
    { icon: "support_agent", label: "AI 비서", href: "/assistant" },
    { icon: "upload_file", label: "이력서 분석 시작", href: "/resume-analyzer" },
    technicalTrack
      ? { icon: "code", label: "CS 퀴즈 세션", href: "/study-quiz" }
      : { icon: "folder_special", label: "포트폴리오/경험 정리", href: "/resume-analyzer/match" },
    { icon: "add_circle", label: "지원 현황 추가", href: "/application-tracker" },
  ];

  return (
    <div className="flex flex-col gap-5">
      {/* Gradient hero card */}
      <div>
        <p className="mb-3 text-sm font-bold text-foreground">
          오늘의 집중
        </p>
        <div
          className="rounded-xl p-5 text-white"
          style={{ background: `linear-gradient(135deg, ${C.primary}, oklch(0.45 0.20 268))` }}
        >
          <p className="font-mono text-[11px] opacity-70">AI INTERVIEW COACH</p>
          <p className="mt-2 text-[15px] font-bold leading-snug">{focusTitle}</p>
          <p className="mt-1 text-xs opacity-75">{focusDesc}</p>
          <Link
            href={focusHref}
            className="mt-4 inline-flex items-center gap-1.5 rounded-lg border border-white/25 bg-white/20 px-3.5 py-1.5 text-xs font-semibold transition-colors hover:bg-white/30"
          >
            <span className="material-symbols-outlined text-sm">play_circle</span>
            {incomplete ? "이어서 연습" : "연습 시작"}
          </Link>
        </div>
      </div>

      {/* Quick actions */}
      <div>
        <p className="mb-3 text-sm font-bold text-foreground">
          빠른 실행
        </p>
        <div className="flex flex-col gap-1.5">
          {quickActions.map((a) => (
            <Link
              key={a.href}
              href={a.href}
              className="flex items-center gap-2.5 rounded-lg border border-border px-3 py-2.5 text-sm font-medium text-muted-foreground transition-colors hover:border-border/80 hover:bg-accent hover:text-foreground"
            >
              <span className="material-symbols-outlined text-base">{a.icon}</span>
              {a.label}
            </Link>
          ))}
        </div>
      </div>

      {/* Upcoming interviews */}
      {upcomingInterviews.length > 0 && (
        <div>
          <p className="mb-3 text-[11px] font-semibold uppercase tracking-widest text-muted-foreground">
            면접 진행 중
          </p>
          <div className="flex flex-col gap-1.5">
            {upcomingInterviews.map((e) => (
              <div
                key={e.id}
                className="flex items-center gap-2.5 rounded-lg border border-border bg-card px-3 py-2.5"
              >
                <div
                  className="flex size-8 flex-shrink-0 items-center justify-center rounded-lg text-xs font-bold text-white"
                  style={{ background: C.amber }}
                >
                  {e.companyName.charAt(0)}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-semibold text-foreground">
                    {e.companyName}
                  </p>
                  <p className="truncate text-xs text-muted-foreground">
                    {e.position ?? "포지션 미지정"}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function ActivityPanel() {
  const { user } = useAuth();
  const { data: resumeSessions = [] } = useResumeSessions();
  const { data: quizSessions = [] } = useCsQuizSessions();
  const { data: entries = [] } = useRecruitmentEntries();
  const technicalTrack = isTechnicalTrack(user?.targetRoles);

  const recentResume = resumeSessions.slice(0, 3);
  const recentEntries = entries.slice(0, 4);

  return (
    <div className="flex flex-col gap-5">
      {/* Recent resume sessions */}
      <div>
        <div className="mb-3 flex items-center justify-between">
          <p className="text-sm font-bold text-foreground">
            최근 면접 세션
          </p>
          <Link href="/resume-analyzer" className="text-xs font-medium text-primary hover:underline">
            전체 보기
          </Link>
        </div>
        {recentResume.length === 0 ? (
          <p className="text-xs text-muted-foreground">세션이 없어요.</p>
        ) : (
          <div className="flex flex-col gap-1.5">
            {recentResume.map((s) => {
              const done = s.answeredQuestionCount;
              const total = s.totalQuestionCount;
              return (
                <Link
                  key={s.id}
                  href={`/resume-analyzer/practice?sessionId=${s.id}`}
                  className="rounded-lg border border-border bg-card px-3 py-2.5 transition-colors hover:bg-accent/50"
                >
                  <p className="truncate text-sm font-semibold text-foreground">
                    {s.title}
                  </p>
                  <div className="mt-1 flex items-center gap-2">
                    <span className="text-xs text-muted-foreground">
                      {s.positionType ?? "포지션 미지정"}
                    </span>
                    <span className="text-muted-foreground/40">·</span>
                    <span className="text-xs text-muted-foreground">
                      {done}/{total}
                    </span>
                  </div>
                </Link>
              );
            })}
          </div>
        )}
      </div>

      {/* Recent applications */}
      <div>
        <div className="mb-3 flex items-center justify-between">
          <p className="text-sm font-bold text-foreground">
            최근 지원
          </p>
          <Link href="/application-tracker" className="text-xs font-medium text-primary hover:underline">
            전체 보기
          </Link>
        </div>
        {recentEntries.length === 0 ? (
          <p className="text-xs text-muted-foreground">지원 기록이 없어요.</p>
        ) : (
          <div className="flex flex-col">
            {recentEntries.map((e, i) => {
              const color = stepColor(e.step);
              return (
                <div
                  key={e.id}
                  className={cn(
                    "flex items-center gap-3 py-2.5",
                    i < recentEntries.length - 1 && "border-b border-border"
                  )}
                >
                  <div
                    className="flex size-8 flex-shrink-0 items-center justify-center rounded-lg text-xs font-bold text-white"
                    style={{ background: stepColor(e.step) }}
                  >
                    {e.companyName.charAt(0)}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold text-foreground">
                      {e.companyName}
                    </p>
                    <p className="truncate text-xs text-muted-foreground">
                      {e.position ?? "포지션 미지정"}
                    </p>
                  </div>
                  <span
                    className="flex-shrink-0 rounded-full px-2 py-0.5 text-xs font-semibold"
                    style={{
                      color,
                      background: `color-mix(in oklab, ${color} 15%, transparent)`,
                    }}
                  >
                    {stepLabel(e.step)}
                  </span>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* CS Quiz stats */}
      {technicalTrack && quizSessions.length > 0 && (
        <div>
          <p className="mb-2 text-sm font-bold text-foreground">
            퀴즈 현황
          </p>
          <div className="rounded-lg border border-border bg-card px-3 py-2.5">
            <p className="font-sans text-[28px] font-bold tracking-[-0.04em]" style={{ color: C.violet }}>
              {quizSessions.length}
            </p>
            <p className="text-xs text-muted-foreground">총 세션 수</p>
          </div>
        </div>
      )}
    </div>
  );
}

export function AppDetailPanel() {
  const pathname = usePathname();
  const [tab, setTab] = useState<"focus" | "activity">("focus");

  const isSessionPage = SESSION_PATHS.some((p) => pathname.startsWith(p));
  if (isSessionPage) return null;

  return (
    <aside className="hidden w-[300px] flex-shrink-0 flex-col border-l border-border bg-card xl:flex">
      {/* Tab bar */}
      <div className="flex gap-1.5 border-b border-border px-5 py-3.5">
        {(["focus", "activity"] as const).map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={cn(
              "rounded-md px-2.5 py-1 text-xs font-semibold transition-colors",
              tab === t
                ? "bg-primary/10 text-primary"
                : "text-muted-foreground hover:text-foreground"
            )}
          >
            {t === "focus" ? "오늘의 집중" : "활동"}
          </button>
        ))}
      </div>

      {/* Panel content */}
      <div className="flex-1 overflow-y-auto p-5 custom-scrollbar">
        {tab === "focus" ? <FocusPanel /> : <ActivityPanel />}
      </div>
    </aside>
  );
}
