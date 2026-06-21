"use client";

import { useEffect, useRef, useState } from "react";
import { InterviewerAvatar } from "./InterviewerAvatar";
import type { ConversationEntry, ConversationSubPhase } from "../hooks/useSpeechInterviewMachine";
import type { SpeechSynthesisHook } from "../hooks/useSpeechSynthesis";
import type { SpeechRecognitionHook } from "../hooks/useSpeechRecognition";

type Props = {
  subPhase: ConversationSubPhase;
  currentAiMessage: string;
  currentBadge: string | null;
  turnIndex: number;
  maxTurns: number;
  conversationLog: ConversationEntry[];
  tts: SpeechSynthesisHook;
  stt: SpeechRecognitionHook;
  onAiSpeakDone: () => void;
  onUserSubmit: (answerText: string) => void;
};

const ANSWER_TIME_LIMIT = 120;

// ── WaveformBar ────────────────────────────────────────────
function WaveformBar({ active, color }: { active: boolean; color: string }) {
  return (
    <>
      <style>{`
        @keyframes waveBar { 0%,100%{transform:scaleY(0.2)} 50%{transform:scaleY(1)} }
      `}</style>
      <div style={{ display: "flex", alignItems: "center", gap: 3, height: 32 }}>
        {Array.from({ length: 24 }, (_, i) => (
          <div
            key={i}
            style={{
              width: 3, borderRadius: 2,
              background: color,
              height: "100%",
              transformOrigin: "center",
              transform: "scaleY(0.2)",
              animation: active ? `waveBar ${0.5 + (i % 5) * 0.12}s ease-in-out infinite ${(i % 7) * 0.08}s` : "none",
              opacity: active ? 0.85 : 0.2,
            }}
          />
        ))}
      </div>
    </>
  );
}

// ── CircularTimer ──────────────────────────────────────────
function CircularTimer({ seconds, maxSeconds }: { seconds: number; maxSeconds: number }) {
  const r = 22;
  const circ = 2 * Math.PI * r;
  const progress = Math.max(0, seconds / maxSeconds);
  const offset = circ * (1 - progress);
  const warn = seconds <= 20;
  const critical = seconds <= 5;
  const color = critical ? "var(--speech-danger)" : warn ? "var(--speech-warning)" : "var(--speech-success)";

  return (
    <>
      <style>{`
        @keyframes timerWarn { 0%,100%{opacity:1} 50%{opacity:0.5} }
      `}</style>
      <div style={{ position: "relative", width: 56, height: 56, display: "flex", alignItems: "center", justifyContent: "center" }}>
        <svg width={56} height={56} viewBox="0 0 56 56" style={{ position: "absolute", inset: 0, transform: "rotate(-90deg)" }}>
          <circle cx="28" cy="28" r={r} fill="none" stroke="rgb(var(--speech-text-rgb) / 0.08)" strokeWidth="3" />
          <circle cx="28" cy="28" r={r} fill="none" stroke={color} strokeWidth="3"
            strokeDasharray={circ} strokeDashoffset={offset}
            strokeLinecap="round"
            style={{ transition: "stroke-dashoffset 1s linear, stroke 0.3s ease" }}
          />
        </svg>
        <span style={{
          fontSize: 15, fontWeight: 700, color, fontFamily: "monospace",
          animation: critical ? "timerWarn 0.8s ease-in-out infinite" : "none",
        }}>
          {seconds}
        </span>
      </div>
    </>
  );
}

// ── ConversationView ───────────────────────────────────────
export function ConversationView({
  subPhase,
  currentAiMessage,
  currentBadge,
  turnIndex,
  maxTurns,
  conversationLog,
  tts,
  stt,
  onAiSpeakDone,
  onUserSubmit,
}: Props) {
  const [timeLeft, setTimeLeft] = useState(ANSWER_TIME_LIMIT);
  const [showLog, setShowLog] = useState(false);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const ttsStartedRef = useRef(false);

  const avatarState =
    subPhase === "AI_THINKING" ? "thinking"
    : subPhase === "AI_SPEAKING" ? "speaking"
    : "listening";

  // AI_SPEAKING: TTS 재생
  useEffect(() => {
    if (subPhase === "AI_SPEAKING" && currentAiMessage && !ttsStartedRef.current) {
      ttsStartedRef.current = true;
      tts.speak(currentAiMessage, () => { onAiSpeakDone(); });
    }
    if (subPhase !== "AI_SPEAKING") {
      ttsStartedRef.current = false;
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subPhase, currentAiMessage]);

  // USER_ANSWERING: STT + 타이머
  useEffect(() => {
    if (subPhase === "USER_ANSWERING") {
      setTimeLeft(ANSWER_TIME_LIMIT);
      stt.start();
      timerRef.current = setInterval(() => {
        setTimeLeft((prev) => {
          if (prev <= 1) { handleSubmit(); return 0; }
          return prev - 1;
        });
      }, 1000);
    } else {
      if (timerRef.current) { clearInterval(timerRef.current); timerRef.current = null; }
      stt.stop();
    }
    return () => { if (timerRef.current) { clearInterval(timerRef.current); timerRef.current = null; } };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subPhase]);

  function handleSubmit() {
    if (subPhase !== "USER_ANSWERING") return;
    stt.stop();
    if (timerRef.current) { clearInterval(timerRef.current); timerRef.current = null; }
    const answerText = stt.transcript.trim() || "(답변 없음)";
    stt.resetTranscript();
    onUserSubmit(answerText);
  }

  const warn = timeLeft <= 20;
  const critical = timeLeft <= 5;

  // 현재 질문 (로그의 마지막 AI 메시지)
  const lastAiLog = conversationLog.filter(e => e.role === "ai").pop();

  return (
    <div className="flex flex-col md:flex-row overflow-hidden" style={{ height: "calc(100vh - 3.5rem)", background: "var(--speech-bg)" }}>

      {/* ── 왼쪽 AI 노드 패널 ── */}
      <div
        className="hidden md:flex md:flex-col md:items-center md:justify-start"
        style={{
          width: 260, flexShrink: 0,
          borderRight: "1px solid rgb(var(--speech-text-rgb) / 0.06)",
          padding: "48px 20px 24px",
          background: "linear-gradient(180deg, color-mix(in srgb, var(--speech-bg) 0%, transparent) 0%, color-mix(in srgb, var(--speech-panel) 60%, transparent) 100%)",
          position: "relative", overflow: "hidden",
        }}>
        {/* 앰비언트 글로우 */}
        <div style={{
          position: "absolute", width: 200, height: 200, borderRadius: "50%",
          background: avatarState === "thinking" ? "rgb(var(--speech-warning-rgb) / 0.08)"
            : avatarState === "speaking" ? "rgb(var(--speech-accent-rgb) / 0.08)"
            : "rgb(var(--speech-success-rgb) / 0.08)",
          filter: "blur(40px)",
          transition: "background 0.8s ease",
          pointerEvents: "none",
        }} />
        <InterviewerAvatar state={avatarState} />
      </div>

      {/* ── 오른쪽 대화 패널 ── */}
      <div style={{ flex: 1, display: "flex", flexDirection: "column", overflow: "hidden", padding: "24px 28px 0" }}>

        {/* ── THINKING ── */}
        {subPhase === "AI_THINKING" && (
          <div style={{ flex: 1, display: "flex", flexDirection: "column", animation: "fadeUp 0.4s ease" }}>
            <div style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 16 }}>
              <div style={{ display: "flex", gap: 6 }}>
                {[0, 1, 2].map((i) => (
                  <div key={i} style={{
                    width: 8, height: 8, borderRadius: "50%", background: "var(--speech-warning)",
                    animation: `pulse 1s ease-in-out ${i * 0.2}s infinite`,
                  }} />
                ))}
              </div>
              <p style={{ fontSize: 14, color: "rgb(var(--speech-text-rgb) / 0.45)", fontWeight: 400 }}>
                다음 질문을 준비하고 있습니다...
              </p>
            </div>

            {/* 이전 로그 미리보기 (흐림) */}
            {conversationLog.length > 0 && (
              <div style={{ opacity: 0.3, pointerEvents: "none", maxHeight: 120, overflow: "hidden", paddingBottom: 24 }}>
                {conversationLog.slice(-2).map((entry, i) => (
                  <div key={i} style={{ display: "flex", justifyContent: entry.role === "ai" ? "flex-start" : "flex-end", marginBottom: 8 }}>
                    <div style={{
                      maxWidth: "75%", padding: "8px 14px", borderRadius: 10, fontSize: 12, lineHeight: 1.6,
                      background: entry.role === "ai" ? "rgb(var(--speech-accent-rgb) / 0.15)" : "rgb(var(--speech-text-rgb) / 0.08)",
                      color: "rgb(var(--speech-text-rgb) / 0.7)",
                      border: entry.role === "ai" ? "1px solid rgb(var(--speech-accent-rgb) / 0.2)" : "1px solid rgb(var(--speech-text-rgb) / 0.08)",
                    }}>
                      {entry.text.slice(0, 80)}{entry.text.length > 80 ? "..." : ""}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* ── SPEAKING ── */}
        {subPhase === "AI_SPEAKING" && (
          <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: 16, animation: "fadeUp 0.4s ease", overflow: "auto", paddingBottom: 8 }}>
            {/* 뱃지 */}
            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              {currentBadge && (
                <span style={{
                  fontSize: 11, fontWeight: 700, padding: "3px 10px", borderRadius: 100,
                  background: "rgb(var(--speech-accent-rgb) / 0.15)", color: "var(--speech-accent-soft)",
                  border: "1px solid rgb(var(--speech-accent-rgb) / 0.25)", letterSpacing: "0.02em",
                }}>{currentBadge}</span>
              )}
              <span style={{ fontSize: 11, color: "rgb(var(--speech-text-rgb) / 0.3)", fontFamily: "monospace" }}>질문 {turnIndex}</span>
            </div>

            {/* 질문 카드 */}
            <div style={{
              background: "color-mix(in srgb, var(--speech-panel) 80%, transparent)", border: "1px solid rgb(var(--speech-accent-rgb) / 0.2)",
              borderRadius: 16, padding: "24px 26px",
              boxShadow: "0 4px 32px rgb(var(--speech-accent-rgb) / 0.08)",
              display: "flex", flexDirection: "column", gap: 16,
            }}>
              <p style={{ fontSize: 17, fontWeight: 600, color: "rgb(var(--speech-text-rgb) / 0.92)", lineHeight: 1.7, letterSpacing: "-0.01em" }}>
                {currentAiMessage}
              </p>

              {/* 웨이브폼 + 건너뛰기 */}
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", borderTop: "1px solid rgb(var(--speech-text-rgb) / 0.06)", paddingTop: 14 }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <div style={{ width: 6, height: 6, borderRadius: "50%", background: "var(--speech-accent)", animation: "recDot 1.2s ease-in-out infinite" }} />
                  <WaveformBar active={true} color="var(--speech-accent)" />
                </div>
                <button
                  onClick={onAiSpeakDone}
                  style={{
                    fontSize: 12, color: "rgb(var(--speech-text-rgb) / 0.3)", background: "none", border: "none", cursor: "pointer",
                    transition: "color 0.15s",
                  }}
                  onMouseEnter={e => (e.currentTarget.style.color = "rgb(var(--speech-text-rgb) / 0.6)")}
                  onMouseLeave={e => (e.currentTarget.style.color = "rgb(var(--speech-text-rgb) / 0.3)")}
                >
                  건너뛰기
                </button>
              </div>
            </div>
          </div>
        )}

        {/* ── LISTENING ── */}
        {subPhase === "USER_ANSWERING" && (
          <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: 14, animation: "fadeUp 0.4s ease", overflow: "hidden" }}>
            {/* 흐린 질문 참조 */}
            <div style={{
              background: "color-mix(in srgb, var(--speech-panel) 40%, transparent)", border: "1px solid rgb(var(--speech-accent-rgb) / 0.1)",
              borderRadius: 12, padding: "14px 18px", opacity: 0.55, flexShrink: 0,
            }}>
              {currentBadge && (
                <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 6 }}>
                  <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 100, background: "rgb(var(--speech-accent-rgb) / 0.12)", color: "var(--speech-accent-soft)", border: "1px solid rgb(var(--speech-accent-rgb) / 0.2)" }}>
                    {currentBadge}
                  </span>
                </div>
              )}
              <p style={{ fontSize: 13, color: "rgb(var(--speech-text-rgb) / 0.7)", lineHeight: 1.6, overflow: "hidden", display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical" as const }}>
                {currentAiMessage}
              </p>
            </div>

            {/* STT 텍스트 영역 */}
            <div style={{
              flex: 1, background: "rgb(var(--speech-text-rgb) / 0.03)", border: "1px solid rgb(var(--speech-text-rgb) / 0.08)",
              borderRadius: 14, padding: "18px 20px", position: "relative", overflow: "auto",
              minHeight: 120,
            }}>
              {stt.transcript ? (
                <p style={{ fontSize: 15, color: "rgb(var(--speech-text-rgb) / 0.88)", lineHeight: 1.8 }}>
                  {stt.transcript}
                  <span style={{ display: "inline-block", width: 2, height: 16, background: "var(--speech-success)", marginLeft: 2, verticalAlign: "middle", animation: "recDot 0.8s ease-in-out infinite" }} />
                </p>
              ) : stt.errorMessage ? (
                <p style={{ fontSize: 14, color: "var(--speech-warning)", lineHeight: 1.6 }}>
                  {stt.errorMessage}
                </p>
              ) : (
                <p style={{ fontSize: 14, color: "rgb(var(--speech-text-rgb) / 0.2)", fontStyle: "italic" }}>말씀해 주세요...</p>
              )}

              {/* REC 표시 */}
              <div style={{ position: "absolute", top: 14, right: 16, display: "flex", alignItems: "center", gap: 6 }}>
                <div style={{ width: 7, height: 7, borderRadius: "50%", background: "var(--speech-danger)", animation: "recDot 1s ease-in-out infinite" }} />
                <span style={{ fontSize: 10, color: "rgb(var(--speech-text-rgb) / 0.3)", fontFamily: "monospace", letterSpacing: "0.06em" }}>REC</span>
              </div>
            </div>

            {/* 타이머 + 제출 */}
            <div style={{ display: "flex", alignItems: "center", gap: 14, paddingBottom: 20, flexShrink: 0 }}>
              <CircularTimer seconds={timeLeft} maxSeconds={ANSWER_TIME_LIMIT} />
              <div style={{ flex: 1 }}>
                <div style={{
                  fontSize: 12, marginBottom: 4, transition: "color 0.3s",
                  color: critical ? "var(--speech-danger)" : warn ? "var(--speech-warning)" : "rgb(var(--speech-text-rgb) / 0.4)",
                }}>
                  {critical ? "⚠ 시간이 거의 없습니다" : warn ? "남은 시간이 얼마 없습니다" : "답변하실 시간이 충분히 있습니다"}
                </div>
                <WaveformBar active={!!stt.transcript} color="var(--speech-success)" />
              </div>
              <button
                onClick={handleSubmit}
                style={{
                  padding: "12px 24px", borderRadius: 10,
                  background: stt.transcript ? "var(--speech-success-strong)" : "rgb(var(--speech-text-rgb) / 0.05)",
                  color: stt.transcript ? "var(--speech-text)" : "rgb(var(--speech-text-rgb) / 0.2)",
                  border: "none", fontSize: 14, fontWeight: 700,
                  cursor: stt.transcript ? "pointer" : "not-allowed",
                  transition: "all 0.2s",
                  boxShadow: stt.transcript ? "0 4px 16px rgb(var(--speech-success-strong-rgb) / 0.35)" : "none",
                }}
                onMouseEnter={e => { if (stt.transcript) e.currentTarget.style.background = "var(--speech-success)"; }}
                onMouseLeave={e => { if (stt.transcript) e.currentTarget.style.background = "var(--speech-success-strong)"; }}
              >
                답변 완료
              </button>
            </div>
          </div>
        )}

        {/* ── 이전 대화 아코디언 ── */}
        {conversationLog.length > 0 && subPhase !== "AI_THINKING" && (
          <div style={{ flexShrink: 0, borderTop: "1px solid rgb(var(--speech-text-rgb) / 0.06)", marginTop: 4 }}>
            <button
              onClick={() => setShowLog(l => !l)}
              style={{
                width: "100%", display: "flex", alignItems: "center", justifyContent: "space-between",
                padding: "10px 0", background: "none", border: "none", cursor: "pointer",
                color: "rgb(var(--speech-text-rgb) / 0.35)", fontSize: 12,
              }}
            >
              <span>이전 대화 보기 ({Math.floor(conversationLog.length / 2)}턴)</span>
              <span style={{ transform: showLog ? "rotate(180deg)" : "none", transition: "transform 0.2s", display: "inline-block" }}>▾</span>
            </button>
            {showLog && (
              <div style={{ maxHeight: 200, overflow: "auto", paddingBottom: 16, display: "flex", flexDirection: "column", gap: 8 }}>
                {conversationLog.map((entry, i) => (
                  <div key={i} style={{ display: "flex", justifyContent: entry.role === "ai" ? "flex-start" : "flex-end" }}>
                    <div style={{
                      maxWidth: "78%", padding: "10px 14px", fontSize: 13, lineHeight: 1.65,
                      background: entry.role === "ai" ? "rgb(var(--speech-accent-rgb) / 0.12)" : "rgb(var(--speech-text-rgb) / 0.07)",
                      color: "rgb(var(--speech-text-rgb) / 0.75)",
                      border: entry.role === "ai" ? "1px solid rgb(var(--speech-accent-rgb) / 0.18)" : "1px solid rgb(var(--speech-text-rgb) / 0.08)",
                      borderRadius: entry.role === "ai" ? "4px 12px 12px 12px" : "12px 4px 12px 12px",
                    }}>
                      {entry.text}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      <style>{`
        @keyframes fadeUp { from{opacity:0;transform:translateY(10px)} to{opacity:1;transform:translateY(0)} }
        @keyframes pulse { 0%,100%{opacity:1;transform:scale(1)} 50%{opacity:0.5;transform:scale(0.8)} }
        @keyframes recDot { 0%,100%{opacity:1;transform:scale(1)} 50%{opacity:0.3;transform:scale(0.7)} }
      `}</style>
    </div>
  );
}
