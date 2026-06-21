"use client";

import { useSearchParams, useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { useSpeechInterview } from "../hooks/useSpeechInterviews";
import type { SpeechInterviewSession, SpeechInterviewQuestion, SpeechFeedback } from "../api/types";

const POLLING_DEADLINE_MS = 5 * 60 * 1000;

// ── 유틸 ──────────────────────────────────────────────────

function scoreColor(s: number): string {
  return s >= 85 ? "var(--speech-success)" : s >= 70 ? "var(--speech-accent)" : "var(--speech-warning)";
}

function formatDuration(session: SpeechInterviewSession): string {
  if (!session.completedAt) return "";
  const ms = new Date(session.completedAt).getTime() - new Date(session.createdAt).getTime();
  const min = Math.round(ms / 60000);
  return min > 0 ? `약 ${min}분` : "";
}

// 참여율을 점수로 사용 (피드백 없을 때)
function sessionScore(session: SpeechInterviewSession): number {
  const total = session.questions.length;
  if (total === 0) return 0;
  const answered = session.questions.filter((q) => q.answer).length;
  return Math.round((answered / total) * 100);
}

// ── 왼쪽 패널 — Score Ring ───────────────────────────────

function ScoreRing({ score }: { score: number }) {
  const r = 40;
  const circ = 2 * Math.PI * r;
  const offset = circ * (1 - score / 100);
  const color = scoreColor(score);
  return (
    <div style={{ position: "relative", width: 100, height: 100, margin: "0 auto 14px" }}>
      <svg width={100} height={100} viewBox="0 0 100 100" style={{ transform: "rotate(-90deg)" }}>
        <circle cx="50" cy="50" r={r} fill="none" stroke="rgb(var(--speech-text-rgb) / 0.06)" strokeWidth="8" />
        <circle cx="50" cy="50" r={r} fill="none" stroke={color} strokeWidth="8"
          strokeDasharray={circ}
          strokeDashoffset={offset}
          strokeLinecap="round"
          style={{ transition: "stroke-dashoffset 1s ease" }}
        />
      </svg>
      <div style={{ position: "absolute", inset: 0, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center" }}>
        <span style={{ fontSize: 28, fontWeight: 700, color, fontFamily: "monospace", letterSpacing: "-0.03em", lineHeight: 1 }}>{score}</span>
        <span style={{ fontSize: 10, color: "rgb(var(--speech-text-rgb) / 0.3)", marginTop: 2 }}>/ 100</span>
      </div>
    </div>
  );
}

// ── 오른쪽 패널 — 질문 카드 ──────────────────────────────

function QuestionCard({ question, index, animating, direction }: {
  question: SpeechInterviewQuestion;
  index: number;
  animating: boolean;
  direction: number;
}) {
  const [showModel, setShowModel] = useState(false);
  const answer = question.answer;
  const feedback = answer?.feedback as SpeechFeedback | undefined;
  const isPending = answer?.feedbackStatus === "PENDING";
  const isCompleted = answer?.feedbackStatus === "COMPLETED";

  const animClass = animating
    ? (direction > 0 ? "slideOutLeft" : "slideOutRight")
    : (direction > 0 ? "slideInRight" : "slideInLeft");

  return (
    <>
      <style>{`
        @keyframes slideInRight  { from{opacity:0;transform:translateX(40px)} to{opacity:1;transform:translateX(0)} }
        @keyframes slideInLeft   { from{opacity:0;transform:translateX(-40px)} to{opacity:1;transform:translateX(0)} }
        @keyframes slideOutLeft  { from{opacity:1;transform:translateX(0)} to{opacity:0;transform:translateX(-40px)} }
        @keyframes slideOutRight { from{opacity:1;transform:translateX(0)} to{opacity:0;transform:translateX(40px)} }
      `}</style>
      <div style={{
        position: "absolute", inset: 0, overflow: "auto", padding: "28px 32px",
        animation: `${animClass} 0.28s ease forwards`,
      }}>
        {/* 뱃지 + 질문 번호 */}
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 18 }}>
          <span style={{
            fontSize: 11, fontWeight: 700, padding: "3px 10px", borderRadius: 100,
            background: "rgb(var(--speech-accent-rgb) / 0.15)", color: "var(--speech-accent-soft)",
            border: "1px solid rgb(var(--speech-accent-rgb) / 0.25)",
          }}>{question.badge || "면접 질문"}</span>
          <span style={{ fontSize: 11, color: "rgb(var(--speech-text-rgb) / 0.3)", fontFamily: "monospace" }}>Q{index + 1}</span>
        </div>

        {/* 질문 박스 */}
        <div style={{
          background: "color-mix(in srgb, var(--speech-panel) 60%, transparent)", border: "1px solid rgb(var(--speech-accent-rgb) / 0.15)",
          borderRadius: 14, padding: "18px 20px", marginBottom: 16,
        }}>
          <div style={{ fontSize: 10, fontFamily: "monospace", color: "rgb(var(--speech-text-rgb) / 0.3)", letterSpacing: "0.06em", marginBottom: 8 }}>QUESTION</div>
          <p style={{ fontSize: 15, fontWeight: 600, color: "rgb(var(--speech-text-rgb) / 0.88)", lineHeight: 1.7, letterSpacing: "-0.01em" }}>
            {question.questionText}
          </p>
        </div>

        {/* 내 답변 */}
        {answer && (
          <div style={{
            background: "rgb(var(--speech-text-rgb) / 0.03)", border: "1px solid rgb(var(--speech-text-rgb) / 0.07)",
            borderRadius: 14, padding: "16px 20px", marginBottom: 16,
          }}>
            <div style={{ fontSize: 10, fontFamily: "monospace", color: "rgb(var(--speech-text-rgb) / 0.3)", letterSpacing: "0.06em", marginBottom: 8 }}>MY ANSWER</div>
            <p style={{ fontSize: 14, color: "rgb(var(--speech-text-rgb) / 0.65)", lineHeight: 1.75 }}>
              {answer.answerText === "(답변 없음)" ? (
                <span style={{ fontStyle: "italic", color: "rgb(var(--speech-text-rgb) / 0.25)" }}>답변 없음</span>
              ) : answer.answerText}
            </p>
          </div>
        )}

        {/* 피드백 생성 중 안내 */}
        {isPending && (
          <div style={{
            display: "flex", alignItems: "center", gap: 10,
            background: "rgb(var(--speech-text-rgb) / 0.03)", border: "1px solid rgb(var(--speech-text-rgb) / 0.07)",
            borderRadius: 12, padding: "14px 18px",
          }}>
            <span className="material-symbols-outlined" style={{ fontSize: 16, color: "rgb(var(--speech-text-rgb) / 0.25)" }}>schedule</span>
            <span style={{ fontSize: 13, color: "rgb(var(--speech-text-rgb) / 0.35)" }}>
              AI 피드백은 잠시 후 스피치 면접 이력에서 확인할 수 있습니다.
            </span>
          </div>
        )}

        {/* 강점 + 개선점 */}
        {isCompleted && feedback && (
          <>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginBottom: 16 }}>
              {/* 강점 */}
              <div style={{
                background: "rgb(var(--speech-success-rgb) / 0.06)", border: "1px solid rgb(var(--speech-success-rgb) / 0.18)",
                borderRadius: 12, padding: 16,
              }}>
                <div style={{ display: "flex", alignItems: "center", gap: 7, marginBottom: 12 }}>
                  <div style={{ width: 6, height: 6, borderRadius: "50%", background: "var(--speech-success)" }} />
                  <span style={{ fontSize: 11, fontWeight: 700, color: "var(--speech-success)", fontFamily: "monospace", letterSpacing: "0.04em" }}>강점</span>
                </div>
                <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                  {(feedback.strengths ?? []).map((s, i) => (
                    <div key={i} style={{ display: "flex", gap: 8, alignItems: "flex-start" }}>
                      <span style={{ color: "var(--speech-success)", flexShrink: 0, marginTop: 2, fontSize: 12 }}>✓</span>
                      <span style={{ fontSize: 13, color: "rgb(var(--speech-text-rgb) / 0.7)", lineHeight: 1.6 }}>{s}</span>
                    </div>
                  ))}
                </div>
              </div>

              {/* 개선점 */}
              <div style={{
                background: "rgb(var(--speech-warning-rgb) / 0.06)", border: "1px solid rgb(var(--speech-warning-rgb) / 0.18)",
                borderRadius: 12, padding: 16,
              }}>
                <div style={{ display: "flex", alignItems: "center", gap: 7, marginBottom: 12 }}>
                  <div style={{ width: 6, height: 6, borderRadius: "50%", background: "var(--speech-warning)" }} />
                  <span style={{ fontSize: 11, fontWeight: 700, color: "var(--speech-warning)", fontFamily: "monospace", letterSpacing: "0.04em" }}>개선점</span>
                </div>
                <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                  {(feedback.improvements ?? []).map((s, i) => (
                    <div key={i} style={{ display: "flex", gap: 8, alignItems: "flex-start" }}>
                      <span style={{ color: "var(--speech-warning)", flexShrink: 0, marginTop: 2, fontSize: 12 }}>→</span>
                      <span style={{ fontSize: 13, color: "rgb(var(--speech-text-rgb) / 0.7)", lineHeight: 1.6 }}>{s}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* 발화 피드백 */}
            {((feedback.deliveryStrengths ?? []).length > 0 || (feedback.deliveryImprovements ?? []).length > 0) && (
              <div style={{
                background: "rgb(var(--speech-accent-rgb) / 0.05)", border: "1px solid rgb(var(--speech-accent-rgb) / 0.14)",
                borderRadius: 12, padding: 16, marginBottom: 16,
              }}>
                <div style={{ display: "flex", alignItems: "center", gap: 7, marginBottom: 12 }}>
                  <span className="material-symbols-outlined" style={{ fontSize: 15, color: "var(--speech-accent)" }}>record_voice_over</span>
                  <span style={{ fontSize: 11, fontWeight: 700, color: "var(--speech-accent-soft)", fontFamily: "monospace", letterSpacing: "0.04em" }}>발화 피드백</span>
                </div>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                  <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                    {(feedback.deliveryStrengths ?? []).map((s, i) => (
                      <div key={i} style={{ display: "flex", gap: 8, alignItems: "flex-start" }}>
                        <span style={{ color: "var(--speech-success)", flexShrink: 0, marginTop: 2, fontSize: 12 }}>✓</span>
                        <span style={{ fontSize: 13, color: "rgb(var(--speech-text-rgb) / 0.68)", lineHeight: 1.6 }}>{s}</span>
                      </div>
                    ))}
                  </div>
                  <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                    {(feedback.deliveryImprovements ?? []).map((s, i) => (
                      <div key={i} style={{ display: "flex", gap: 8, alignItems: "flex-start" }}>
                        <span style={{ color: "var(--speech-warning)", flexShrink: 0, marginTop: 2, fontSize: 12 }}>→</span>
                        <span style={{ fontSize: 13, color: "rgb(var(--speech-text-rgb) / 0.68)", lineHeight: 1.6 }}>{s}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            )}

            {/* 모범 답변 (토글) */}
            {feedback.suggestedAnswer && (
              <div style={{
                background: "rgb(var(--speech-text-rgb) / 0.03)", border: "1px solid rgb(var(--speech-text-rgb) / 0.07)",
                borderRadius: 12, overflow: "hidden", marginBottom: 12,
              }}>
                <button
                  onClick={() => setShowModel(v => !v)}
                  style={{
                    width: "100%", display: "flex", alignItems: "center", justifyContent: "space-between",
                    padding: "12px 16px", background: "none", border: "none", cursor: "pointer",
                    color: "rgb(var(--speech-text-rgb) / 0.45)", fontSize: 12,
                  }}
                >
                  <span>모범 답변 보기</span>
                  <span style={{ transform: showModel ? "rotate(180deg)" : "none", transition: "transform 0.2s", display: "inline-block" }}>▾</span>
                </button>
                {showModel && (
                  <div style={{ padding: "0 16px 16px", borderTop: "1px solid rgb(var(--speech-text-rgb) / 0.06)", paddingTop: 12 }}>
                    <p style={{ fontSize: 13, color: "rgb(var(--speech-text-rgb) / 0.55)", lineHeight: 1.75 }}>{feedback.suggestedAnswer}</p>
                  </div>
                )}
              </div>
            )}

            {/* 꼬리 질문 */}
            {feedback.followups && feedback.followups.length > 0 && (
              <div style={{
                background: "rgb(var(--speech-accent-rgb) / 0.05)", border: "1px solid rgb(var(--speech-accent-rgb) / 0.12)",
                borderRadius: 12, padding: "14px 16px",
              }}>
                <div style={{ fontSize: 10, fontFamily: "monospace", color: "rgb(var(--speech-text-rgb) / 0.3)", letterSpacing: "0.06em", marginBottom: 10 }}>예상 꼬리 질문</div>
                <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                  {feedback.followups.map((q, i) => (
                    <p key={i} style={{ fontSize: 13, color: "rgb(var(--speech-text-rgb) / 0.5)", lineHeight: 1.6 }}>• {q}</p>
                  ))}
                </div>
              </div>
            )}

            {answer.feedbackStatus === "FAILED" && (
              <p style={{ fontSize: 12, color: "rgb(var(--speech-danger-rgb) / 0.6)", fontStyle: "italic" }}>피드백 생성에 실패했습니다.</p>
            )}
          </>
        )}
      </div>
    </>
  );
}

// ── ResultContent ──────────────────────────────────────────

function ResultContent({ session, isPollingExpired, onRefresh }: {
  session: SpeechInterviewSession;
  isPollingExpired: boolean;
  onRefresh: () => void;
}) {
  const router = useRouter();
  const [activeCard, setActiveCard] = useState(0);
  const [direction, setDirection] = useState(1);
  const [animating, setAnimating] = useState(false);

  const questions = session.questions;
  const total = questions.length;
  const answeredCount = questions.filter((q) => q.answer).length;
  const score = sessionScore(session);
  const color = scoreColor(score);
  const duration = formatDuration(session);

  function goTo(idx: number) {
    if (animating || idx === activeCard) return;
    setDirection(idx > activeCard ? 1 : -1);
    setAnimating(true);
    setTimeout(() => { setActiveCard(idx); setAnimating(false); }, 280);
  }

  const prev = () => goTo(Math.max(0, activeCard - 1));
  const next = () => goTo(Math.min(total - 1, activeCard + 1));

  return (
    <div style={{ height: "calc(100vh - 0px)", display: "flex", flexDirection: "column", background: "var(--speech-bg)", overflow: "hidden" }}>
      {/* 헤더 */}
      <header style={{
        height: 52, flexShrink: 0,
        borderBottom: "1px solid rgb(var(--speech-text-rgb) / 0.07)",
        display: "flex", alignItems: "center", padding: "0 24px", gap: 12,
        background: "color-mix(in srgb, var(--speech-bg) 95%, transparent)", backdropFilter: "blur(12px)",
      }}>
        <button
          onClick={() => router.push("/speech-interview")}
          style={{
            display: "flex", alignItems: "center", gap: 6, padding: "5px 10px", borderRadius: 6,
            border: "1px solid rgb(var(--speech-text-rgb) / 0.1)", background: "none",
            color: "rgb(var(--speech-text-rgb) / 0.45)", fontSize: 12, cursor: "pointer",
          }}
        >‹ 스피치 면접</button>
        <div style={{ display: "flex", alignItems: "center", gap: 8, marginLeft: 8 }}>
          <div style={{ width: 6, height: 6, borderRadius: "50%", background: "var(--speech-success)" }} />
          <span style={{ fontSize: 13, fontWeight: 600, color: "rgb(var(--speech-text-rgb) / 0.8)", whiteSpace: "nowrap" }}>
            {session.title} · 결과
          </span>
        </div>
        <div style={{ flex: 1 }} />
        {isPollingExpired && (
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <span style={{ fontSize: 12, color: "rgb(var(--speech-warning-rgb) / 0.85)" }}>
              피드백 생성에 시간이 걸리고 있어요.
            </span>
            <button
              onClick={onRefresh}
              style={{
                padding: "5px 10px", borderRadius: 6,
                border: "1px solid rgb(var(--speech-warning-rgb) / 0.28)",
                background: "rgb(var(--speech-warning-rgb) / 0.08)",
                color: "var(--speech-warning)", fontSize: 12, cursor: "pointer", fontWeight: 600,
              }}
            >지금 새로고침</button>
          </div>
        )}
        <button
          onClick={() => router.push("/speech-interview")}
          style={{
            padding: "5px 14px", borderRadius: 6, border: "none",
            background: "var(--speech-accent-strong)", color: "var(--speech-text)", fontSize: 12, cursor: "pointer", fontWeight: 600,
          }}
        >다시 면접하기</button>
      </header>

      {/* 본문 */}
      <div style={{ flex: 1, display: "flex", overflow: "hidden", minHeight: 0 }}>

        {/* ── 왼쪽 고정 패널 ── */}
        <div
          className="hidden md:flex"
          style={{
            width: 300, flexShrink: 0,
            borderRight: "1px solid rgb(var(--speech-text-rgb) / 0.07)",
            overflow: "auto", padding: "24px 20px",
            flexDirection: "column", gap: 24,
          }}>
          {/* 세션 정보 */}
          <div>
            <div style={{ fontSize: 10, fontFamily: "monospace", color: "rgb(var(--speech-text-rgb) / 0.3)", letterSpacing: "0.08em", marginBottom: 8 }}>
              {session.positionType ?? "스피치 면접"}
            </div>
            <div style={{ fontSize: 15, fontWeight: 700, color: "rgb(var(--speech-text-rgb) / 0.9)", marginBottom: 4 }}>수고하셨습니다!</div>
            <div style={{ fontSize: 12, color: "rgb(var(--speech-text-rgb) / 0.35)" }}>
              {total}턴 완료{duration ? ` · ${duration}` : ""}
            </div>
          </div>

          {/* 참여율 링 */}
          <div style={{
            background: "rgb(var(--speech-text-rgb) / 0.03)", border: "1px solid rgb(var(--speech-text-rgb) / 0.07)",
            borderRadius: 14, padding: 20, textAlign: "center",
          }}>
            <div style={{ fontSize: 11, color: "rgb(var(--speech-text-rgb) / 0.35)", fontFamily: "monospace", letterSpacing: "0.06em", marginBottom: 12 }}>
              답변 참여율
            </div>
            <ScoreRing score={score} />
            <div style={{ fontSize: 12, color, fontWeight: 600 }}>
              {answeredCount}/{total}개 답변 완료
            </div>
          </div>

          {/* 질문별 목록 */}
          <div>
            <div style={{ fontSize: 10, fontFamily: "monospace", color: "rgb(var(--speech-text-rgb) / 0.3)", letterSpacing: "0.06em", marginBottom: 12 }}>
              질문별 피드백
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              {questions.map((q, i) => {
                const hasAnswer = !!q.answer;
                const isCompleted = q.answer?.feedbackStatus === "COMPLETED";
                const dotColor = isCompleted ? "var(--speech-success)" : hasAnswer ? "var(--speech-warning)" : "rgb(var(--speech-text-rgb) / 0.2)";
                return (
                  <button
                    key={q.id}
                    onClick={() => goTo(i)}
                    style={{
                      display: "flex", alignItems: "center", gap: 10, padding: "7px 10px", borderRadius: 8,
                      background: activeCard === i ? "rgb(var(--speech-accent-rgb) / 0.12)" : "transparent",
                      border: activeCard === i ? "1px solid rgb(var(--speech-accent-rgb) / 0.25)" : "1px solid transparent",
                      cursor: "pointer", transition: "all 0.15s", textAlign: "left",
                    }}
                  >
                    <div style={{ width: 6, height: 6, borderRadius: "50%", background: dotColor, flexShrink: 0 }} />
                    <span style={{
                      fontSize: 11, flex: 1,
                      color: activeCard === i ? "rgb(var(--speech-text-rgb) / 0.85)" : "rgb(var(--speech-text-rgb) / 0.4)",
                    }}>
                      Q{i + 1}. {q.badge || "면접 질문"}
                    </span>
                    <span style={{ fontSize: 10, fontFamily: "monospace", color: dotColor }}>
                      {isCompleted ? "완료" : hasAnswer ? "분석중" : "미답변"}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>
        </div>

        {/* ── 오른쪽 캐러셀 ── */}
        <div style={{ flex: 1, display: "flex", flexDirection: "column", overflow: "hidden", minWidth: 0 }}>
          {/* 네비게이션 바 */}
          <div style={{
            height: 48, flexShrink: 0,
            borderBottom: "1px solid rgb(var(--speech-text-rgb) / 0.07)",
            display: "flex", alignItems: "center", padding: "0 24px", gap: 12,
          }}>
            <button
              onClick={prev} disabled={activeCard === 0}
              style={{
                width: 28, height: 28, borderRadius: 7, border: "1px solid rgb(var(--speech-text-rgb) / 0.1)",
                background: "none", color: activeCard === 0 ? "rgb(var(--speech-text-rgb) / 0.15)" : "rgb(var(--speech-text-rgb) / 0.6)",
                cursor: activeCard === 0 ? "not-allowed" : "pointer", fontSize: 14,
                display: "flex", alignItems: "center", justifyContent: "center",
              }}
            >‹</button>
            <div style={{ display: "flex", gap: 5 }}>
              {questions.map((_, i) => (
                <button
                  key={i} onClick={() => goTo(i)}
                  style={{
                    width: activeCard === i ? 20 : 7, height: 7, borderRadius: 4,
                    background: activeCard === i ? "var(--speech-accent)" : "rgb(var(--speech-text-rgb) / 0.12)",
                    border: "none", cursor: "pointer", padding: 0, transition: "all 0.25s ease",
                  }}
                />
              ))}
            </div>
            <button
              onClick={next} disabled={activeCard === total - 1}
              style={{
                width: 28, height: 28, borderRadius: 7, border: "1px solid rgb(var(--speech-text-rgb) / 0.1)",
                background: "none", color: activeCard === total - 1 ? "rgb(var(--speech-text-rgb) / 0.15)" : "rgb(var(--speech-text-rgb) / 0.6)",
                cursor: activeCard === total - 1 ? "not-allowed" : "pointer", fontSize: 14,
                display: "flex", alignItems: "center", justifyContent: "center",
              }}
            >›</button>
            <div style={{ flex: 1 }} />
            <span style={{ fontSize: 11, fontFamily: "monospace", color: "rgb(var(--speech-text-rgb) / 0.3)" }}>
              {activeCard + 1} / {total}
            </span>
          </div>

          {/* 카드 영역 */}
          <div style={{ flex: 1, overflow: "hidden", position: "relative" }}>
            {questions.length > 0 ? (
              <QuestionCard
                question={questions[activeCard]}
                index={activeCard}
                animating={animating}
                direction={direction}
              />
            ) : (
              <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: "100%", color: "rgb(var(--speech-text-rgb) / 0.3)", fontSize: 14 }}>
                질문이 없습니다.
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

// ── 메인 Export ────────────────────────────────────────────

export function SpeechInterviewResultPage() {
  const searchParams = useSearchParams();
  const queryClient = useQueryClient();
  const sessionId = searchParams.get("sessionId");
  const parsedId = sessionId ? parseInt(sessionId, 10) : null;
  const pendingStartRef = useRef<number | null>(null);
  const [pollingExpired, setPollingExpired] = useState(false);

  const { data: session, isLoading, error } = useSpeechInterview(parsedId, !pollingExpired);
  const hasPending = (session?.questions ?? []).some((q) => q.answer?.feedbackStatus === "PENDING");
  const displaySession = session;

  useEffect(() => {
    if (!hasPending) {
      pendingStartRef.current = null;
      return;
    }

    if (pendingStartRef.current === null) {
      pendingStartRef.current = Date.now();
    }

    const startedAt = pendingStartRef.current;
    const remainingMs = Math.max(POLLING_DEADLINE_MS - (Date.now() - startedAt), 0);
    const timeoutId = window.setTimeout(() => setPollingExpired(true), remainingMs);

    return () => window.clearTimeout(timeoutId);
  }, [hasPending]);

  if (!parsedId) {
    return (
      <div style={{ display: "flex", minHeight: "100vh", alignItems: "center", justifyContent: "center", background: "var(--speech-bg)" }}>
        <p style={{ fontSize: 14, color: "rgb(var(--speech-text-rgb) / 0.4)" }}>잘못된 접근입니다.</p>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div style={{ display: "flex", minHeight: "100vh", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 12, background: "var(--speech-bg)" }}>
        <span className="material-symbols-outlined animate-spin" style={{ fontSize: 24, color: "var(--speech-accent)" }}>progress_activity</span>
        <p style={{ fontSize: 14, color: "rgb(var(--speech-text-rgb) / 0.4)" }}>결과를 불러오는 중...</p>
      </div>
    );
  }

  if (error || !displaySession) {
    return (
      <div style={{ display: "flex", minHeight: "100vh", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 12, background: "var(--speech-bg)" }}>
        <span className="material-symbols-outlined" style={{ fontSize: 24, color: "var(--speech-danger)" }}>error</span>
        <p style={{ fontSize: 14, color: "rgb(var(--speech-text-rgb) / 0.4)" }}>결과를 불러오지 못했습니다.</p>
      </div>
    );
  }

  return (
    <ResultContent
      session={displaySession}
      isPollingExpired={pollingExpired && hasPending}
      onRefresh={() => {
        setPollingExpired(false);
        pendingStartRef.current = null;
        queryClient.invalidateQueries({ queryKey: ["speechInterview", parsedId] });
      }}
    />
  );
}
