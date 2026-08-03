"use client";

import Link from "next/link";
import { useResumeSessions } from "../hooks/useResumeSessions";
import { useDeleteResumeSession } from "../hooks/useResumeMutations";
import type { ResumeSession } from "../api/types";
import { ALL_POSITIONS } from "../constants";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

function positionLabel(type: string | null) {
  if (!type) return "미지정";
  return ALL_POSITIONS.find((p) => p.id === type)?.label ?? type;
}

type StatusConfig = {
  label: string;
  dotColor: string;
  barColor: string;
  textColor: string;
};

function statusConfig(status: string): StatusConfig {
  switch (status) {
    case "COMPLETED":
      return {
        label: "완료",
        dotColor: "bg-teal",
        barColor: "bg-teal",
        textColor: "text-teal",
      };
    case "QUESTIONS_READY":
      return {
        label: "연습 가능",
        dotColor: "bg-primary",
        barColor: "bg-primary",
        textColor: "text-primary",
      };
    case "CREATED":
      return {
        label: "생성 중",
        dotColor: "bg-amber-500",
        barColor: "bg-amber-500",
        textColor: "text-amber-600",
      };
    case "FAILED":
      return {
        label: "실패",
        dotColor: "bg-destructive",
        barColor: "bg-destructive",
        textColor: "text-destructive",
      };
    default:
      return {
        label: status,
        dotColor: "bg-muted-foreground/40",
        barColor: "bg-muted",
        textColor: "text-muted-foreground",
      };
  }
}

function formatLastActivity(isoString: string | null): string {
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

export function ResumeAnalyzerHubView() {
  const { data: sessions = [], isLoading, error } = useResumeSessions();
  const deleteSession = useDeleteResumeSession();

  const completedCount = sessions.filter((s) => s.status === "COMPLETED").length;
  const readyCount = sessions.filter((s) => s.status === "QUESTIONS_READY").length;

  function onDelete(e: React.MouseEvent, sessionId: number) {
    e.preventDefault();
    e.stopPropagation();
    if (!confirm("이 세션을 삭제하시겠어요?")) return;
    deleteSession.mutate(sessionId);
  }

  return (
    <div className="flex flex-col gap-6">
      {/* ── Hero ────────────────────────────────────────────── */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-[22px] font-bold text-foreground">AI 면접 준비</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            이력서를 분석해 맞춤 면접 질문과 AI 피드백을 받아보세요.
          </p>
        </div>
        <div className="flex shrink-0 flex-wrap items-center gap-2">
          <Link
            href="/resume-analyzer/match"
            className="inline-flex items-center gap-2 rounded-xl border border-border bg-card px-4 py-2.5 text-sm font-semibold text-foreground transition-all hover:bg-accent/50"
          >
            <span className="material-symbols-outlined text-sm">contract_edit</span>
            공고 매칭 분석
          </Link>
          <Link
            href="/speech-interview"
            className="inline-flex items-center gap-2 rounded-xl border border-border bg-card px-4 py-2.5 text-sm font-semibold text-foreground transition-all hover:bg-accent/50"
          >
            <span className="material-symbols-outlined text-sm">record_voice_over</span>
            스피치 면접
          </Link>
          <Link
            href="/culture-fit"
            className="inline-flex items-center gap-2 rounded-xl border border-border bg-card px-4 py-2.5 text-sm font-semibold text-foreground transition-all hover:bg-accent/50"
          >
            <span className="material-symbols-outlined text-sm">diversity_3</span>
            컬처핏 면접
          </Link>
          <Link
            href="/resume-analyzer/practice"
            className="inline-flex items-center gap-2 rounded-xl bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition-all hover:bg-primary/90"
          >
            <span className="material-symbols-outlined text-sm">smart_toy</span>
            텍스트 면접 연습
          </Link>
        </div>
      </div>

      {/* ── Stats strip ─────────────────────────────────────── */}
      {!isLoading && sessions.length > 0 && (
        <div className="grid grid-cols-3 gap-3 sm:max-w-lg">
          {[
            {
              label: "전체 세션",
              value: sessions.length,
              icon: "description",
              iconCls: "text-muted-foreground bg-muted",
            },
            {
              label: "완료",
              value: completedCount,
              icon: "task_alt",
              iconCls: "text-teal bg-teal/10",
            },
            {
              label: "연습 가능",
              value: readyCount,
              icon: "play_circle",
              iconCls: "text-primary bg-primary/10",
            },
          ].map((stat) => (
            <div
              key={stat.label}
              className="flex items-center gap-3 rounded-xl border border-border bg-card p-4"
            >
              <div
                className={cn(
                  "flex size-10 shrink-0 items-center justify-center rounded-lg",
                  stat.iconCls
                )}
              >
                <span className="material-symbols-outlined text-[20px]">{stat.icon}</span>
              </div>
              <div>
                <p className="text-xl font-bold leading-none text-foreground">{stat.value}</p>
                <p className="mt-1 text-xs text-muted-foreground">{stat.label}</p>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* ── Session list ─────────────────────────────────────── */}
      <div className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <span className="text-sm font-semibold text-foreground">세션 이력</span>
          {sessions.length > 0 && (
            <span className="text-xs text-muted-foreground">{sessions.length}개</span>
          )}
        </div>

        {error ? (
          <div className="flex items-center gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-4">
            <span className="material-symbols-outlined text-destructive">error</span>
            <p className="text-sm text-destructive">
              세션 목록을 불러오지 못했습니다.{" "}
              {error instanceof Error ? error.message : ""}
            </p>
          </div>
        ) : isLoading ? (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {[1, 2, 3].map((i) => (
              <div
                key={i}
                className="animate-pulse rounded-xl border border-border bg-card p-4"
              >
                <div className="h-3 w-1/2 rounded bg-muted" />
                <div className="mt-3 h-4 w-3/4 rounded bg-muted" />
                <div className="mt-4 h-1 w-full rounded-full bg-muted" />
                <div className="mt-4 h-px w-full bg-muted" />
                <div className="mt-3 h-3 w-1/4 rounded bg-muted" />
              </div>
            ))}
          </div>
        ) : sessions.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-border p-10 text-center">
            <span className="material-symbols-outlined text-4xl text-muted-foreground/40">
              description
            </span>
            <p className="text-sm text-muted-foreground">아직 면접 연습 기록이 없어요.</p>
            <Link
              href="/resume-analyzer/practice"
              className="text-sm font-semibold text-primary hover:underline"
            >
              첫 세션 시작하기
            </Link>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {sessions.map((session) => (
              <SessionCard
                key={session.id}
                session={session}
                onDelete={onDelete}
                isDeleting={deleteSession.isPending}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function SessionCard({
  session,
  onDelete,
  isDeleting,
}: {
  session: ResumeSession;
  onDelete: (e: React.MouseEvent, id: number) => void;
  isDeleting: boolean;
}) {
  const total = session.totalQuestionCount ?? session.questions.length;
  const answered = session.answeredQuestionCount ?? 0;
  const progressPercent = total > 0 ? (answered / total) * 100 : 0;
  const lastActivityLabel = formatLastActivity(
    session.lastAttemptAt ?? session.completedAt ?? session.createdAt
  );
  const cfg = statusConfig(session.status);

  return (
    <Link
      href={`/resume-analyzer/practice?sessionId=${session.id}`}
      className="group block h-full rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/60"
    >
      <div
        className={cn(
          "flex h-full flex-col overflow-hidden rounded-xl border border-border bg-card p-4 transition-colors hover:bg-accent/30"
        )}
      >
        {/* Status row */}
        <div className="mb-3 flex items-center justify-between">
          <div className="flex items-center gap-1.5">
            <div className={cn("size-1.5 rounded-full", cfg.dotColor)} />
            <span className={cn("text-xs font-semibold", cfg.textColor)}>{cfg.label}</span>
          </div>
          <Button
            variant="ghost"
            size="icon"
            className="size-6 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100 hover:text-destructive"
            disabled={isDeleting}
            onClick={(e) => onDelete(e, session.id)}
          >
            <span className="material-symbols-outlined text-sm">delete</span>
          </Button>
        </div>

        {/* Title */}
        <p className="line-clamp-2 text-sm font-semibold leading-snug text-foreground">
          {session.title}
        </p>

        {/* Position badge */}
        <div className="mt-2 min-h-[22px]">
          {session.positionType && (
            <span className="rounded-md bg-muted px-2 py-0.5 font-mono text-[11px] font-semibold text-muted-foreground">
              {positionLabel(session.positionType)}
            </span>
          )}
        </div>

        {/* Progress bar */}
        <div className="mt-3">
          <div className="h-1 w-full overflow-hidden rounded-full bg-muted">
            <div
              className={cn("h-full rounded-full transition-all duration-300", cfg.barColor)}
              style={{ width: `${Math.max(progressPercent, answered > 0 ? 3 : 0)}%` }}
            />
          </div>
        </div>

        {/* Footer */}
        <div className="mt-auto flex items-center justify-between border-t border-border pt-3 mt-3">
          <span className="text-xs tabular-nums text-muted-foreground">{answered}/{total} 답변</span>
          <span className="text-xs text-muted-foreground">{lastActivityLabel}</span>
        </div>
      </div>
    </Link>
  );
}
