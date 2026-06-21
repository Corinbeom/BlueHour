"use client";

import Link from "next/link";
import { useAuth } from "@/features/auth/hooks/useAuth";
import { isTechnicalTrack } from "@/features/member/roleCategory";
import { useRecruitmentEntries } from "@/features/application-tracker/hooks/useRecruitmentEntries";
import { useCsQuizSessions } from "@/features/study-quiz/hooks/useCsQuizSessions";
import { useResumeSessions } from "@/features/resume-analyzer/hooks/useResumeSessions";
import type { ResumeSession } from "@/features/resume-analyzer/api/types";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

// Pulse palette — matches PA colors from design
const PA = {
  primary: "var(--color-primary)",
  green: "var(--color-teal)",
  amber: "var(--color-chart-3)",
  violet: "var(--color-chart-4)",
  red: "var(--color-destructive)",
};

/* ── Helpers ─────────────────────────────────────────────────────────────── */

function topicLabel(topic: string) {
  const map: Record<string, string> = {
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
  return map[topic] ?? topic;
}

function formatRelativeDate(isoString: string | null): string {
  if (!isoString) return "";
  const date = new Date(isoString);
  const diffMs = Date.now() - date.getTime();
  const diffMin = Math.floor(diffMs / 60_000);
  if (diffMin < 1) return "방금 전";
  if (diffMin < 60) return `${diffMin}분 전`;
  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return `${diffHour}시간 전`;
  const diffDay = Math.floor(diffHour / 24);
  if (diffDay < 7) return `${diffDay}일 전`;
  return date.toLocaleDateString("ko-KR");
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

function stepColor(step: string) {
  switch (step) {
    case "INTERVIEWING": return PA.amber;
    case "OFFERED": return PA.green;
    case "APPLIED":
    case "DOC_PASSED": return PA.primary;
    case "TEST_PHASE": return PA.violet;
    case "REJECTED": return PA.red;
    default: return PA.violet;
  }
}

/* ── Sub-components ──────────────────────────────────────────────────────── */

function StatCard({
  value,
  label,
  icon,
  color,
  trend,
}: {
  value: number;
  label: string;
  icon: string;
  color: string;
  trend?: string;
}) {
  return (
    <div className="rounded-xl border border-border bg-card p-[18px_20px] transition-shadow hover:shadow-md hover:shadow-black/5">
      <div className="mb-3.5 flex items-center justify-between">
        <span className="text-xs font-semibold text-muted-foreground">{label}</span>
        <div
          className="flex size-7 items-center justify-center rounded-lg"
          style={{ background: `color-mix(in oklab, ${color} 15%, transparent)` }}
        >
          <span
            className="material-symbols-outlined text-[15px]"
            style={{ color }}
          >
            {icon}
          </span>
        </div>
      </div>
      <p
        className="font-sans text-[38px] font-bold leading-none tracking-[-0.04em]"
        style={{ color }}
      >
        {value}
      </p>
      {trend && (
        <p className="mt-2 text-[11px] text-muted-foreground">{trend}</p>
      )}
    </div>
  );
}

/* ── Main Component ──────────────────────────────────────────────────────── */

export function DashboardView() {
  const { user } = useAuth();
  const { data: entries = [], error: entriesError } = useRecruitmentEntries();
  const { data: quizSessions = [], error: quizError } = useCsQuizSessions();
  const { data: resumeSessions = [], error: resumeError } = useResumeSessions();

  const displayName = user?.displayName ?? user?.email?.split("@")[0] ?? "사용자";
  const technicalTrack = isTechnicalTrack(user?.targetRoles);
  const hasDataError = !!(entriesError || quizError || resumeError);

  const interviewingCount = entries.filter((e) => e.step === "INTERVIEWING").length;
  const offeredCount = entries.filter((e) => e.step === "OFFERED").length;

  const recentEntries = entries.slice(0, 4);
  const recentResumeSessions = resumeSessions.slice(0, 2);
  const recentQuizSessions = quizSessions.slice(0, 2);

  // Today's focus: find incomplete session or suggest next action
  const incompleteResume = resumeSessions.find(
    (s) => s.status === "QUESTIONS_READY" && s.answeredQuestionCount < s.totalQuestionCount
  );
  const focusHref = incompleteResume
    ? `/resume-analyzer/practice?sessionId=${incompleteResume.id}`
    : "/resume-analyzer";
  const focusTitle = incompleteResume
    ? incompleteResume.title
    : "이력서 기반 맞춤 면접 연습";
  const focusDesc = incompleteResume
    ? `${incompleteResume.positionType ?? "포지션 미지정"} · 남은 질문 ${
        incompleteResume.totalQuestionCount - incompleteResume.answeredQuestionCount
      }개`
    : "이력서/포트폴리오 분석 → AI 맞춤 질문 생성";

  const today = new Date().toLocaleDateString("ko-KR", {
    year: "numeric",
    month: "long",
    day: "numeric",
    weekday: "short",
  });

  return (
    <>
      {/* Data Error Banner */}
      {hasDataError && (
        <div className="flex items-center gap-3 rounded-xl border border-destructive/50 bg-destructive/5 px-4 py-3">
          <span className="material-symbols-outlined text-destructive">warning</span>
          <p className="text-sm text-destructive">
            일부 데이터를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.
          </p>
        </div>
      )}

      {/* ① Greeting strip */}
      <div className="flex items-baseline justify-between">
        <div>
          <h1 className="font-sans text-xl font-bold tracking-[-0.02em] text-foreground">
            오늘도 당신을 응원합니다, {displayName}님
          </h1>
          <p className="mt-0.5 text-[13px] text-muted-foreground">{today}</p>
        </div>
        <div className="flex items-center gap-2">
          <Link
            href="/assistant"
            className={cn(buttonVariants({ variant: "outline", size: "lg" }), "gap-1.5")}
          >
            <span className="material-symbols-outlined text-sm">support_agent</span>
            AI 비서
          </Link>
          <Link
            href="/resume-analyzer/report"
            className={cn(buttonVariants({ size: "lg" }), "gap-1.5")}
          >
            <span className="material-symbols-outlined text-sm">analytics</span>
            면접 리포트 확인
          </Link>
        </div>
      </div>

      {/* ② 4-stat row — Pulse style */}
      <section className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <StatCard
          value={entries.length}
          label="총 지원"
          icon="work"
          color={PA.primary}
          trend={
            interviewingCount > 0
              ? `면접 ${interviewingCount}건 진행 중`
              : offeredCount > 0
              ? `합격 ${offeredCount}건`
              : undefined
          }
        />
        <StatCard
          value={interviewingCount}
          label="면접 진행"
          icon="record_voice_over"
          color={PA.amber}
        />
        {technicalTrack ? (
          <StatCard
            value={quizSessions.length}
            label="퀴즈 세션"
            icon="quiz"
            color={PA.violet}
          />
        ) : (
          <StatCard
            value={recentEntries.length}
            label="최근 지원"
            icon="work_history"
            color={PA.violet}
          />
        )}
        <StatCard
          value={resumeSessions.length}
          label="면접 세션"
          icon="description"
          color={PA.green}
        />
      </section>

      {/* ③ Two-column: AI feature cards + recent applications */}
      <section className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        {/* AI feature cards */}
        <div className="flex flex-col gap-3">
          {/* Today's focus hero */}
          <Link href={focusHref}>
            <div className="group rounded-xl border border-border bg-card p-[18px_20px] transition-all hover:border-primary/50 hover:shadow-md hover:shadow-black/5">
              <div className="mb-3 flex items-center gap-2.5">
                <div
                  className="flex size-8 items-center justify-center rounded-lg"
                  style={{ background: `color-mix(in oklab, ${PA.primary} 15%, transparent)` }}
                >
                  <span className="material-symbols-outlined text-[17px]" style={{ color: PA.primary }}>
                    description
                  </span>
                </div>
                <span className="text-xs font-bold text-muted-foreground">AI 이력서 분석</span>
              </div>
              <p className="text-sm font-semibold text-foreground">{focusTitle}</p>
              <p className="mt-1 text-[12px] text-muted-foreground">{focusDesc}</p>
              <p
                className="mt-4 flex items-center gap-1 text-[13px] font-semibold"
                style={{ color: PA.primary }}
              >
                {incompleteResume ? "이어서 연습하기" : "연습 시작하기"}
                <span className="material-symbols-outlined text-sm">arrow_forward</span>
              </p>
            </div>
          </Link>

          {technicalTrack ? (
            <Link href="/study-quiz">
              <div className="group rounded-xl border border-border bg-card p-[18px_20px] transition-all hover:border-chart-4/50 hover:shadow-md hover:shadow-black/5">
                <div className="mb-3 flex items-center gap-2.5">
                  <div
                    className="flex size-8 items-center justify-center rounded-lg"
                    style={{ background: `color-mix(in oklab, ${PA.violet} 15%, transparent)` }}
                  >
                    <span className="material-symbols-outlined text-[17px]" style={{ color: PA.violet }}>
                      quiz
                    </span>
                  </div>
                  <span className="text-xs font-bold text-muted-foreground">CS 퀴즈</span>
                </div>
                {recentQuizSessions.length > 0 ? (
                  <>
                    <p className="text-sm font-semibold text-foreground">
                      {recentQuizSessions[0].title}
                    </p>
                    <p className="mt-1 text-[12px] text-muted-foreground">
                      {(Array.isArray(recentQuizSessions[0].topics)
                        ? recentQuizSessions[0].topics.slice(0, 2).map(topicLabel).join(" · ")
                        : "") || "토픽 미지정"}
                    </p>
                  </>
                ) : (
                  <>
                    <p className="text-sm font-semibold text-foreground">
                      CS 기술 면접 실력을 점검해 보세요
                    </p>
                    <p className="mt-1 text-[12px] text-muted-foreground">
                      OS · 네트워크 · DB · Spring · Java 외 4개 토픽
                    </p>
                  </>
                )}
                <p
                  className="mt-4 flex items-center gap-1 text-[13px] font-semibold"
                  style={{ color: PA.violet }}
                >
                  {recentQuizSessions.length > 0 ? "계속하기" : "퀴즈 시작하기"}
                  <span className="material-symbols-outlined text-sm">arrow_forward</span>
                </p>
              </div>
            </Link>
          ) : (
            <Link href="/resume-analyzer/match">
              <div className="group rounded-xl border border-border bg-card p-[18px_20px] transition-all hover:border-chart-4/50 hover:shadow-md hover:shadow-black/5">
                <div className="mb-3 flex items-center gap-2.5">
                  <div
                    className="flex size-8 items-center justify-center rounded-lg"
                    style={{ background: `color-mix(in oklab, ${PA.violet} 15%, transparent)` }}
                  >
                    <span className="material-symbols-outlined text-[17px]" style={{ color: PA.violet }}>
                      folder_special
                    </span>
                  </div>
                  <span className="text-xs font-bold text-muted-foreground">포트폴리오/경험 정리</span>
                </div>
                <p className="text-sm font-semibold text-foreground">
                  지원 직무와 맞는 경험 키워드를 점검하세요
                </p>
                <p className="mt-1 text-[12px] text-muted-foreground">
                  이력서, 공고, 포트폴리오의 연결도를 함께 확인
                </p>
                <p
                  className="mt-4 flex items-center gap-1 text-[13px] font-semibold"
                  style={{ color: PA.violet }}
                >
                  직무 적합도 확인
                  <span className="material-symbols-outlined text-sm">arrow_forward</span>
                </p>
              </div>
            </Link>
          )}
        </div>

        {/* Recent applications */}
        <div className="rounded-xl border border-border bg-card overflow-hidden">
          <div className="flex items-center justify-between border-b border-border px-5 py-4">
            <span className="text-[13px] font-bold text-foreground">최근 지원</span>
            <Link
              href="/application-tracker"
              className="text-[12px] font-medium text-primary hover:underline"
            >
              전체 보기
            </Link>
          </div>
          {recentEntries.length === 0 ? (
            <div className="flex flex-col items-center justify-center gap-3 py-10 text-center">
              <span className="material-symbols-outlined text-3xl text-muted-foreground/40">work</span>
              <p className="text-xs text-muted-foreground">아직 지원 기록이 없어요.</p>
              <Link href="/application-tracker" className={buttonVariants({ variant: "outline", size: "sm" })}>
                지원 추가하기
              </Link>
            </div>
          ) : (
            recentEntries.map((e, i) => {
              const color = stepColor(e.step);
              return (
                <div
                  key={e.id}
                  className={cn(
                    "flex cursor-pointer items-center gap-3 px-5 py-3 transition-colors hover:bg-accent/50",
                    i < recentEntries.length - 1 && "border-b border-border"
                  )}
                >
                  <div
                    className="flex size-[30px] flex-shrink-0 items-center justify-center rounded-lg text-[11px] font-bold text-white"
                    style={{ background: stepColor(e.step) }}
                  >
                    {e.companyName.charAt(0)}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="text-[13px] font-semibold text-foreground">{e.companyName}</p>
                    <p className="truncate text-[11px] text-muted-foreground">
                      {e.position ?? "포지션 미지정"}
                    </p>
                  </div>
                  <div className="flex-shrink-0 text-right">
                    <span
                      className="rounded-full px-2 py-0.5 text-[11px] font-semibold"
                      style={{
                        color,
                        background: `color-mix(in oklab, ${color} 15%, transparent)`,
                      }}
                    >
                      {stepLabel(e.step)}
                    </span>
                  </div>
                </div>
              );
            })
          )}
        </div>
      </section>

      {/* ④ Recent resume sessions */}
      <section>
        <div className="mb-3 flex items-center justify-between">
          <p className="text-sm font-semibold text-foreground">최근 면접 세션</p>
          <Link
            href="/resume-analyzer"
            className="flex items-center gap-0.5 text-xs text-muted-foreground transition-colors hover:text-foreground"
          >
            전체 보기
            <span className="material-symbols-outlined text-sm">arrow_forward</span>
          </Link>
        </div>
        {resumeSessions.length === 0 ? (
          <div className="flex flex-col items-center gap-2 rounded-xl border border-dashed border-border bg-card px-5 py-10 text-center">
            <span className="material-symbols-outlined text-3xl text-muted-foreground/40">record_voice_over</span>
            <p className="text-sm font-semibold text-foreground">아직 면접 세션이 없어요</p>
            <p className="text-xs text-muted-foreground">이력서를 업로드하고 AI 맞춤 면접 연습을 시작해보세요</p>
            <Link
              href="/resume-analyzer"
              className={cn(buttonVariants({ variant: "outline", size: "sm" }), "mt-2 gap-1.5")}
            >
              <span className="material-symbols-outlined text-sm">upload_file</span>
              면접 세션 시작하기
            </Link>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {recentResumeSessions.map((s) => (
              <ResumeSessionCard key={s.id} session={s} />
            ))}
          </div>
        )}
      </section>
    </>
  );
}

/* ── ResumeSessionCard ───────────────────────────────────────────────────── */

function ResumeSessionCard({ session }: { session: ResumeSession }) {
  const done = session.answeredQuestionCount;
  const total = session.totalQuestionCount;
  const progressPct = total > 0 ? (done / total) * 100 : 0;

  return (
    <Link href={`/resume-analyzer/practice?sessionId=${session.id}`}>
      <div className="group rounded-xl border border-border bg-card p-4 transition-colors hover:bg-accent/30">
        <div className="flex items-start gap-3">
          <span className="material-symbols-outlined mt-0.5 shrink-0 text-xl text-muted-foreground">
            description
          </span>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold text-foreground">{session.title}</p>
            <p className="mt-0.5 text-[11px] text-muted-foreground">
              {session.positionType ?? "포지션 미지정"} ·{" "}
              {formatRelativeDate(session.lastAttemptAt ?? session.completedAt ?? session.createdAt)}
            </p>
          </div>
        </div>
        <div className="mt-3">
          <div className="mb-1 flex items-center justify-between text-[11px] text-muted-foreground">
            <span>
              <span className="font-bold text-foreground">{done}</span>/{total} 답변
            </span>
            <span>{Math.round(progressPct)}%</span>
          </div>
          <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
            <div
              className="h-full rounded-full transition-all duration-300"
              style={{
                width: `${Math.max(progressPct, done > 0 ? 4 : 0)}%`,
                background: PA.green,
              }}
            />
          </div>
        </div>
      </div>
    </Link>
  );
}
