"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useResumeSessions } from "@/features/resume-analyzer/hooks/useResumeSessions";
import type { ResumeSession } from "@/features/resume-analyzer/api/types";
import { useSpeechInterviews } from "../hooks/useSpeechInterviews";
import type { SpeechInterviewSession } from "../api/types";
import type { InterviewMode } from "../hooks/useSpeechInterviewMachine";
import { cn } from "@/lib/utils";

type Props = {
  onStart: (session: ResumeSession, mode: InterviewMode) => void;
  isCreatingSession: boolean;
};

function formatDate(isoString: string): string {
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

type StatusCfg = { label: string; dot: string; bar: string; text: string; border: string };

function speechStatusConfig(status: string): StatusCfg {
  switch (status) {
    case "COMPLETED":
      return { label: "완료", dot: "bg-emerald-400", bar: "bg-emerald-500", text: "text-emerald-400", border: "border-l-emerald-500" };
    case "IN_PROGRESS":
      return { label: "진행 중", dot: "bg-amber-400", bar: "bg-amber-500", text: "text-amber-400", border: "border-l-amber-500" };
    default:
      return { label: "대기", dot: "bg-blue-400", bar: "bg-blue-500", text: "text-blue-400", border: "border-l-blue-500" };
  }
}

function PastSessionCard({ session }: { session: SpeechInterviewSession }) {
  const router = useRouter();
  const answeredCount = session.questions.filter((q) => q.answer).length;
  const totalCount = session.questions.length;
  const progressPct = totalCount > 0 ? (answeredCount / totalCount) * 100 : 0;
  const cfg = speechStatusConfig(session.status);

  return (
    <button
      onClick={() => router.push(`/speech-interview/result?sessionId=${session.id}`)}
      className={cn(
        "group flex w-full flex-col overflow-hidden rounded-xl border border-white/8 bg-white/[0.03] p-4 text-left transition-all hover:bg-white/[0.06] hover:border-white/15",
        "border-l-[3px]", cfg.border
      )}
    >
      {/* 상태 행 */}
      <div className="mb-2.5 flex items-center justify-between">
        <div className="flex items-center gap-1.5">
          <div className={cn("size-1.5 rounded-full", cfg.dot)} />
          <span className={cn("text-[11px] font-semibold", cfg.text)}>{cfg.label}</span>
        </div>
        <span className="text-[10px] text-white/25">{formatDate(session.createdAt)}</span>
      </div>

      {/* 제목 */}
      <p className="line-clamp-2 text-sm font-semibold leading-snug text-white/80">{session.title}</p>

      {/* 포지션 뱃지 */}
      <div className="mt-1.5 min-h-[18px]">
        {session.positionType && (
          <span className="rounded bg-white/[0.07] px-1.5 py-0.5 font-mono text-[10px] font-semibold text-white/35">
            {session.positionType}
          </span>
        )}
      </div>

      {/* 진행 바 */}
      <div className="mt-3">
        <div className="h-0.5 w-full overflow-hidden rounded-full bg-white/[0.07]">
          <div
            className={cn("h-full rounded-full transition-all duration-300", cfg.bar)}
            style={{ width: `${Math.max(progressPct, answeredCount > 0 ? 3 : 0)}%` }}
          />
        </div>
      </div>

      {/* 하단 */}
      <div className="mt-2.5 flex items-center justify-between border-t border-white/[0.05] pt-2.5">
        <span className="text-[11px] tabular-nums text-white/30">{answeredCount}/{totalCount} 답변</span>
        <span className={cn("text-[11px] font-semibold", cfg.text)}>
          {session.status === "COMPLETED" ? "결과 보기 →" : "이어하기 →"}
        </span>
      </div>
    </button>
  );
}

export function InterviewIntro({ onStart, isCreatingSession }: Props) {
  const { data: sessions = [], isLoading } = useResumeSessions();
  const { data: pastSessions = [], isLoading: isPastLoading } = useSpeechInterviews();

  const [showOnboarding, setShowOnboarding] = useState(() => {
    if (typeof window === "undefined") return false;
    return !localStorage.getItem("speech-interview-onboarded");
  });
  const [selectedId, setSelectedId] = useState<number | null>(null);

  function dismissOnboarding() {
    localStorage.setItem("speech-interview-onboarded", "1");
    setShowOnboarding(false);
  }

  const readySessions = sessions.filter(
    (s) => s.status === "QUESTIONS_READY" || s.status === "COMPLETED"
  );
  const selected = readySessions.find((s) => s.id === selectedId) ?? null;

  function handleStart() {
    if (!selected || isCreatingSession) return;
    onStart(selected, "voice");
  }

  const canStart = selected !== null && !isCreatingSession;

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-8 px-6 py-12">
      {/* 헤더 */}
      <div className="text-center">
        <div className="mx-auto mb-4 flex size-16 items-center justify-center rounded-2xl bg-blue-500/15">
          <span className="material-symbols-outlined text-3xl text-blue-400">record_voice_over</span>
        </div>
        <h1 className="text-2xl font-bold text-white">스피치 면접</h1>
        <p className="mt-2 text-sm text-white/45">
          AI 면접관이 이력서 기반으로 대화하며 질문합니다.
        </p>
      </div>

      {/* ── STEP 1: 세션 선택 ── */}
      <div className="w-full max-w-md">
        <p className="mb-3 flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-white/40">
          <span className="flex size-4 items-center justify-center rounded-full bg-blue-500/30 text-[10px] font-bold text-blue-400">1</span>
          면접 세션 선택
        </p>

        {isLoading ? (
          <div className="space-y-2">
            {[1, 2].map((i) => (
              <div key={i} className="h-16 animate-pulse rounded-xl bg-white/5" />
            ))}
          </div>
        ) : readySessions.length === 0 ? (
          <div className="rounded-xl border border-dashed border-white/10 p-6 text-center">
            <p className="text-sm text-white/40">면접 연습 가능한 세션이 없습니다.</p>
            <p className="mt-1 text-xs text-white/25">이력서 분석에서 먼저 질문을 생성해 주세요.</p>
            <Link
              href="/resume-analyzer"
              className="mt-4 inline-flex h-9 items-center justify-center gap-1.5 rounded-lg bg-blue-500 px-3 text-xs font-semibold text-white transition-colors hover:bg-blue-400"
            >
              <span className="material-symbols-outlined text-base">upload_file</span>
              이력서 분석 시작
            </Link>
          </div>
        ) : (
          <div className="max-h-48 space-y-2 overflow-y-auto pr-1">
            {readySessions.map((s) => (
              <button
                key={s.id}
                onClick={() => setSelectedId(s.id)}
                className={cn(
                  "w-full rounded-xl border px-4 py-3 text-left transition-all",
                  selectedId === s.id
                    ? "border-blue-500/50 bg-blue-500/10 ring-1 ring-blue-500/30"
                    : "border-white/8 bg-white/[0.03] hover:bg-white/[0.06]"
                )}
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-semibold text-white/90">{s.title}</p>
                    <p className="mt-0.5 text-xs text-white/40">
                      {s.positionType ?? "포지션 미지정"} · AI 대화형 면접
                    </p>
                  </div>
                  {selectedId === s.id && (
                    <span className="material-symbols-outlined shrink-0 text-sm text-blue-400">check_circle</span>
                  )}
                </div>
              </button>
            ))}
          </div>
        )}
      </div>

      {/* ── 시작 버튼 ── */}
      <button
        disabled={!canStart}
        onClick={handleStart}
        className={cn(
          "w-full max-w-md rounded-xl py-4 text-sm font-bold transition-all duration-200",
          canStart
            ? "bg-blue-600 text-white shadow-lg shadow-blue-600/30 hover:bg-blue-500 hover:shadow-blue-500/40"
            : "cursor-not-allowed bg-white/5 text-white/20"
        )}
      >
        {isCreatingSession ? (
          <span className="flex items-center justify-center gap-2">
            <span className="material-symbols-outlined animate-spin text-sm">progress_activity</span>
            세션 생성 중...
          </span>
        ) : !selected ? (
          "세션을 선택해 주세요"
        ) : (
          "면접 시작하기"
        )}
      </button>

      {/* ── 과거 스피치 면접 이력 ── */}
      {(isPastLoading || pastSessions.length > 0) && (
        <div className="w-full max-w-md">
          <div className="mb-3 flex items-center justify-between">
            <p className="text-xs font-semibold uppercase tracking-wider text-white/40">
              과거 스피치 면접 이력
            </p>
            {pastSessions.length > 0 && (
              <span className="text-xs text-white/25">{pastSessions.length}개</span>
            )}
          </div>

          {isPastLoading ? (
            <div className="grid grid-cols-2 gap-2">
              {[1, 2].map((i) => (
                <div key={i} className="h-28 animate-pulse rounded-xl bg-white/5" />
              ))}
            </div>
          ) : (
            <div className="grid max-h-64 grid-cols-2 gap-2 overflow-y-auto pr-1">
              {pastSessions.map((s) => (
                <PastSessionCard key={s.id} session={s} />
              ))}
            </div>
          )}
        </div>
      )}

      {/* ── 온보딩 오버레이 (첫 방문 시) ── */}
      {showOnboarding && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/70 backdrop-blur-sm px-6">
          <div className="w-full max-w-md rounded-2xl border border-white/10 bg-[var(--speech-panel)] p-6 shadow-2xl">
            {/* 헤더 */}
            <div className="mb-5 text-center">
              <div className="mx-auto mb-3 flex size-12 items-center justify-center rounded-2xl bg-blue-500/15">
                <span className="material-symbols-outlined text-2xl text-blue-400">record_voice_over</span>
              </div>
              <h2 className="text-lg font-bold text-white">스피치 면접 가이드</h2>
              <p className="mt-1 text-xs text-white/45">실전처럼 말하며 AI 코칭을 받아보세요</p>
            </div>

            {/* 진행 단계 */}
            <p className="mb-3 text-[10px] font-semibold uppercase tracking-wider text-white/30">이렇게 진행돼요</p>
            <div className="mb-5 space-y-3">
              {[
                {
                  step: "1",
                  title: "AI 면접관 질문",
                  desc: "AI 면접관이 이력서를 분석해 첫 질문을 드립니다",
                  icon: "smart_toy",
                },
                {
                  step: "2",
                  title: "답변 + 꼬리 질문",
                  desc: "마이크로 답변하세요. AI가 답변에 따라 꼬리 질문 또는 새 주제로 전환합니다",
                  icon: "mic",
                },
                {
                  step: "3",
                  title: "AI 분석 결과",
                  desc: "최대 8턴 후 강점·개선점·모범 답변을 확인하세요",
                  icon: "auto_awesome",
                },
              ].map(({ step, title, desc, icon }) => (
                <div key={step} className="flex items-start gap-3">
                  <div className="flex size-6 shrink-0 items-center justify-center rounded-full bg-blue-500/15 text-[10px] font-bold text-blue-400">
                    {step}
                  </div>
                  <div className="flex-1">
                    <div className="flex items-center gap-1.5">
                      <span className="material-symbols-outlined text-[14px] text-white/40">{icon}</span>
                      <p className="text-xs font-semibold text-white/80">{title}</p>
                    </div>
                    <p className="mt-0.5 text-[11px] leading-snug text-white/40">{desc}</p>
                  </div>
                </div>
              ))}
            </div>

            {/* 팁 */}
            <p className="mb-3 text-[10px] font-semibold uppercase tracking-wider text-white/30">시작 전 팁</p>
            <div className="mb-6 space-y-2 rounded-xl border border-white/6 bg-white/[0.03] p-3">
              {[
                { icon: "sensors", text: "조용한 환경에서 진행하세요" },
                { icon: "videocam", text: "카메라 사용 시 시선·자세 분석이 추가됩니다" },
                { icon: "timer", text: "질문당 약 2분의 답변 시간이 주어집니다" },
              ].map(({ icon, text }) => (
                <div key={text} className="flex items-center gap-2">
                  <span className="material-symbols-outlined text-[14px] text-white/30">{icon}</span>
                  <p className="text-[11px] text-white/50">{text}</p>
                </div>
              ))}
            </div>

            {/* CTA */}
            <button
              onClick={dismissOnboarding}
              className="w-full rounded-xl bg-blue-600 py-3 text-sm font-bold text-white shadow-lg shadow-blue-600/30 transition-all hover:bg-blue-500 hover:shadow-blue-500/40"
            >
              확인했습니다
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
