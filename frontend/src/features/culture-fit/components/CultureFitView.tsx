"use client";

import { useMemo, useState } from "react";
import { ALL_POSITIONS } from "@/features/resume-analyzer/constants";
import { cn } from "@/lib/utils";
import type { CultureFitQuestion, CultureFitSession } from "../api/types";
import {
  useCompleteCultureFitSession,
  useCreateCultureFitSession,
  useCultureFitSession,
  useCultureFitSessions,
  useGenerateCultureFitFeedback,
  useScrapeCultureFitUrl,
  useSubmitCultureFitAnswer,
} from "../hooks/useCultureFitSession";

const EMPTY_SESSIONS: CultureFitSession[] = [];
const EMPTY_QUESTIONS: CultureFitQuestion[] = [];

function formatDate(isoString: string | null): string {
  if (!isoString) return "";
  return new Date(isoString).toLocaleDateString("ko-KR", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function positionLabel(type: string | null) {
  if (!type) return "포지션 미지정";
  return ALL_POSITIONS.find((position) => position.id === type)?.label ?? type;
}

function sessionTitle(session: CultureFitSession) {
  return session.companyName?.trim() || "이름 없는 회사";
}

export function CultureFitView() {
  const [companyName, setCompanyName] = useState("");
  const [cultureUrl, setCultureUrl] = useState("");
  const [companyCultureText, setCompanyCultureText] = useState("");
  const [jobDescriptionText, setJobDescriptionText] = useState("");
  const [positionType, setPositionType] = useState("");
  const [selectedSessionId, setSelectedSessionId] = useState<number | null>(null);
  const [answerDrafts, setAnswerDrafts] = useState<Record<number, string>>({});

  const sessionsQuery = useCultureFitSessions();
  const sessions = sessionsQuery.data ?? EMPTY_SESSIONS;
  const activeSessionId = selectedSessionId ?? sessions[0]?.id ?? null;
  const sessionQuery = useCultureFitSession(activeSessionId);
  const scrapeMutation = useScrapeCultureFitUrl();
  const createMutation = useCreateCultureFitSession();
  const answerMutation = useSubmitCultureFitAnswer(activeSessionId);
  const feedbackMutation = useGenerateCultureFitFeedback(activeSessionId);
  const completeMutation = useCompleteCultureFitSession();

  const activeSession = sessionQuery.data ?? null;
  const questions = activeSession?.questions ?? EMPTY_QUESTIONS;
  const completedCount = questions.filter((q) => q.feedbackStatus === "COMPLETED").length;
  const canComplete = questions.length > 0 && completedCount === questions.length;
  const isCreating = createMutation.isPending;

  const cultureLength = companyCultureText.trim().length;
  const canCreate = cultureLength >= 100 && cultureLength <= 5000 && !isCreating;

  const sortedQuestions = useMemo(
    () => [...questions].sort((a, b) => a.orderIndex - b.orderIndex),
    [questions],
  );

  async function onScrape() {
    const extractedText = await scrapeMutation.mutateAsync(cultureUrl);
    setCompanyCultureText(extractedText);
  }

  async function onCreate() {
    const session = await createMutation.mutateAsync({
      companyName,
      companyCultureText,
      jobDescriptionText,
      positionType: positionType || null,
    });
    setSelectedSessionId(session.id);
  }

  async function onSubmitAnswer(question: CultureFitQuestion) {
    const answerText = answerDrafts[question.id]?.trim() || "";
    if (!answerText) return;
    await answerMutation.mutateAsync({
      sessionId: activeSessionId as number,
      questionId: question.id,
      answerText,
    });
  }

  async function onGenerateFeedback(question: CultureFitQuestion) {
    await feedbackMutation.mutateAsync({
      sessionId: activeSessionId as number,
      questionId: question.id,
    });
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[360px_minmax(0,1fr)]">
      <section className="flex flex-col gap-5">
        <div className="rounded-xl border border-border bg-card p-5">
          <div className="flex items-start gap-3">
            <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <span className="material-symbols-outlined text-[20px]">diversity_3</span>
            </div>
            <div>
              <h1 className="text-[20px] font-bold text-foreground">컬처핏 면접</h1>
              <p className="mt-1 text-sm leading-6 text-muted-foreground">
                회사 문화 자료와 JD를 바탕으로 가치 정렬 질문과 피드백을 생성합니다.
              </p>
            </div>
          </div>
        </div>

        <div className="rounded-xl border border-border bg-card p-5">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-sm font-semibold text-foreground">새 세션 만들기</h2>
            <span className="text-xs text-muted-foreground">{cultureLength}/5000</span>
          </div>

          <div className="mt-4 flex flex-col gap-3">
            <label className="flex flex-col gap-1.5">
              <span className="text-xs font-medium text-muted-foreground">회사명</span>
              <input
                value={companyName}
                onChange={(e) => setCompanyName(e.target.value)}
                className="h-10 rounded-lg border border-border bg-background px-3 text-sm outline-none transition-colors focus:border-primary"
                placeholder="예: BlueHour"
              />
            </label>

            <label className="flex flex-col gap-1.5">
              <span className="text-xs font-medium text-muted-foreground">회사 문화 URL</span>
              <div className="flex gap-2">
                <input
                  value={cultureUrl}
                  onChange={(e) => setCultureUrl(e.target.value)}
                  className="h-10 min-w-0 flex-1 rounded-lg border border-border bg-background px-3 text-sm outline-none transition-colors focus:border-primary"
                  placeholder="https://..."
                />
                <button
                  type="button"
                  onClick={onScrape}
                  disabled={!cultureUrl.trim() || scrapeMutation.isPending}
                  className="inline-flex h-10 shrink-0 items-center gap-1.5 rounded-lg border border-border px-3 text-sm font-semibold text-foreground transition-colors hover:bg-accent disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <span className="material-symbols-outlined text-[18px]">
                    {scrapeMutation.isPending ? "progress_activity" : "travel_explore"}
                  </span>
                  추출
                </button>
              </div>
              {scrapeMutation.error && (
                <span className="text-xs text-destructive">
                  {scrapeMutation.error instanceof Error
                    ? scrapeMutation.error.message
                    : "URL에서 텍스트를 가져올 수 없습니다."}
                </span>
              )}
            </label>

            <label className="flex flex-col gap-1.5">
              <span className="text-xs font-medium text-muted-foreground">기업 문화/가치 텍스트</span>
              <textarea
                value={companyCultureText}
                onChange={(e) => setCompanyCultureText(e.target.value)}
                className="min-h-36 resize-y rounded-lg border border-border bg-background px-3 py-2 text-sm leading-6 outline-none transition-colors focus:border-primary"
                placeholder="팀 소개, 핵심 가치, 일하는 방식 등을 붙여넣으세요. 최소 100자 이상 필요합니다."
              />
            </label>

            <label className="flex flex-col gap-1.5">
              <span className="text-xs font-medium text-muted-foreground">채용공고 JD</span>
              <textarea
                value={jobDescriptionText}
                onChange={(e) => setJobDescriptionText(e.target.value)}
                className="min-h-24 resize-y rounded-lg border border-border bg-background px-3 py-2 text-sm leading-6 outline-none transition-colors focus:border-primary"
                placeholder="선택 입력"
              />
            </label>

            <label className="flex flex-col gap-1.5">
              <span className="text-xs font-medium text-muted-foreground">포지션</span>
              <select
                value={positionType}
                onChange={(e) => setPositionType(e.target.value)}
                className="h-10 rounded-lg border border-border bg-background px-3 text-sm outline-none transition-colors focus:border-primary"
              >
                <option value="">선택 안 함</option>
                {ALL_POSITIONS.map((position) => (
                  <option key={position.id} value={position.id}>
                    {position.label}
                  </option>
                ))}
              </select>
            </label>

            <button
              type="button"
              onClick={onCreate}
              disabled={!canCreate}
              className="inline-flex h-11 items-center justify-center gap-2 rounded-lg bg-primary px-4 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
            >
              <span className="material-symbols-outlined text-[18px]">
                {isCreating ? "progress_activity" : "auto_awesome"}
              </span>
              질문 생성
            </button>
            {createMutation.error && (
              <p className="text-xs text-destructive">
                {createMutation.error instanceof Error
                  ? createMutation.error.message
                  : "세션 생성에 실패했습니다."}
              </p>
            )}
          </div>
        </div>

        <div className="rounded-xl border border-border bg-card p-5">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold text-foreground">세션 이력</h2>
            <span className="text-xs text-muted-foreground">{sessions.length}개</span>
          </div>
          <div className="mt-3 flex flex-col gap-2">
            {sessionsQuery.isLoading ? (
              <div className="h-20 animate-pulse rounded-lg bg-muted" />
            ) : sessions.length === 0 ? (
              <p className="rounded-lg border border-dashed border-border p-4 text-sm text-muted-foreground">
                아직 컬처핏 세션이 없습니다.
              </p>
            ) : (
              sessions.map((session) => (
                <button
                  key={session.id}
                  type="button"
                  onClick={() => setSelectedSessionId(session.id)}
                  className={cn(
                    "rounded-lg border p-3 text-left transition-colors",
                    activeSessionId === session.id
                      ? "border-primary bg-primary/5"
                      : "border-border hover:bg-accent/50",
                  )}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="truncate text-sm font-semibold text-foreground">
                      {sessionTitle(session)}
                    </span>
                    <span className="shrink-0 text-xs text-muted-foreground">
                      {session.questionCount}문항
                    </span>
                  </div>
                  <div className="mt-1 flex items-center justify-between gap-2 text-xs text-muted-foreground">
                    <span>{positionLabel(session.positionType)}</span>
                    <span>{formatDate(session.createdAt)}</span>
                  </div>
                </button>
              ))
            )}
          </div>
        </div>
      </section>

      <section className="min-w-0">
        {!activeSession ? (
          <div className="flex min-h-[520px] flex-col items-center justify-center rounded-xl border border-dashed border-border bg-card p-10 text-center">
            <span className="material-symbols-outlined text-5xl text-muted-foreground/40">
              psychology_alt
            </span>
            <h2 className="mt-4 text-lg font-bold text-foreground">기업 문화 자료를 입력하세요</h2>
            <p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground">
              URL에서 가져온 텍스트를 확인한 뒤 질문을 생성하면 이 영역에서 답변 연습을 진행합니다.
            </p>
          </div>
        ) : (
          <div className="flex flex-col gap-4">
            <div className="rounded-xl border border-border bg-card p-5">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <p className="text-xs font-medium text-muted-foreground">
                    {positionLabel(activeSession.positionType)}
                  </p>
                  <h2 className="mt-1 text-xl font-bold text-foreground">
                    {sessionTitle(activeSession)}
                  </h2>
                </div>
                <div className="flex items-center gap-2">
                  <span className="rounded-full bg-muted px-3 py-1 text-xs font-semibold text-muted-foreground">
                    {completedCount}/{questions.length} 피드백 완료
                  </span>
                  <button
                    type="button"
                    onClick={() => completeMutation.mutate(activeSession.id)}
                    disabled={!canComplete || completeMutation.isPending || activeSession.status === "COMPLETED"}
                    className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-border px-3 text-sm font-semibold text-foreground transition-colors hover:bg-accent disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    <span className="material-symbols-outlined text-[18px]">task_alt</span>
                    완료
                  </button>
                </div>
              </div>
              {completeMutation.error && (
                <p className="mt-3 text-xs text-destructive">
                  {completeMutation.error instanceof Error
                    ? completeMutation.error.message
                    : "세션 완료 처리에 실패했습니다."}
                </p>
              )}
            </div>

            {sessionQuery.isLoading ? (
              <div className="h-80 animate-pulse rounded-xl bg-muted" />
            ) : (
              sortedQuestions.map((question) => (
                <QuestionCard
                  key={question.id}
                  question={question}
                  draft={answerDrafts[question.id] ?? question.answerText ?? ""}
                  onDraftChange={(value) =>
                    setAnswerDrafts((prev) => ({ ...prev, [question.id]: value }))
                  }
                  onSubmit={() => onSubmitAnswer(question)}
                  onFeedback={() => onGenerateFeedback(question)}
                  isSubmitting={answerMutation.isPending}
                  isGenerating={feedbackMutation.isPending}
                  isReadOnly={activeSession.status === "COMPLETED"}
                />
              ))
            )}
          </div>
        )}
      </section>
    </div>
  );
}

function QuestionCard({
  question,
  draft,
  onDraftChange,
  onSubmit,
  onFeedback,
  isSubmitting,
  isGenerating,
  isReadOnly,
}: {
  question: CultureFitQuestion;
  draft: string;
  onDraftChange: (value: string) => void;
  onSubmit: () => void;
  onFeedback: () => void;
  isSubmitting: boolean;
  isGenerating: boolean;
  isReadOnly: boolean;
}) {
  const hasAnswer = Boolean(question.answerText?.trim());
  const feedback = question.feedbackStatus === "COMPLETED" ? question.feedback : null;

  return (
    <article className="rounded-xl border border-border bg-card p-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="rounded-full bg-primary/10 px-2.5 py-1 text-xs font-semibold text-primary">
              {question.badge}
            </span>
            <span className="text-xs text-muted-foreground">출제 가능성 {question.likelihood}%</span>
          </div>
          <h3 className="mt-3 text-base font-bold leading-7 text-foreground">
            {question.questionText}
          </h3>
          {question.intention && (
            <p className="mt-2 text-sm leading-6 text-muted-foreground">{question.intention}</p>
          )}
        </div>
        <span
          className={cn(
            "shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold",
            question.feedbackStatus === "COMPLETED"
              ? "bg-teal/10 text-teal"
              : question.feedbackStatus === "FAILED"
                ? "bg-destructive/10 text-destructive"
                : "bg-muted text-muted-foreground",
          )}
        >
          {question.feedbackStatus === "COMPLETED"
            ? "피드백 완료"
            : question.feedbackStatus === "FAILED"
              ? "피드백 실패"
              : "대기"}
        </span>
      </div>

      <div className="mt-4 flex flex-col gap-3">
        <textarea
          value={draft}
          onChange={(e) => onDraftChange(e.target.value)}
          disabled={isReadOnly}
          className="min-h-28 resize-y rounded-lg border border-border bg-background px-3 py-2 text-sm leading-6 outline-none transition-colors focus:border-primary"
          placeholder="답변을 입력하세요."
        />
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={onSubmit}
            disabled={isReadOnly || !draft.trim() || isSubmitting}
            className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-primary px-3 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <span className="material-symbols-outlined text-[18px]">
              {isSubmitting ? "progress_activity" : "send"}
            </span>
            답변 저장
          </button>
          <button
            type="button"
            onClick={onFeedback}
            disabled={isReadOnly || !hasAnswer || isGenerating}
            className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-border px-3 text-sm font-semibold text-foreground transition-colors hover:bg-accent disabled:cursor-not-allowed disabled:opacity-50"
          >
            <span className="material-symbols-outlined text-[18px]">
              {isGenerating ? "progress_activity" : "rate_review"}
            </span>
            피드백 생성
          </button>
        </div>
      </div>

      {feedback && (
        <div className="mt-5 grid gap-3">
          <FeedbackBlock icon="verified" title="문화 정렬도" items={[feedback.alignmentNote]} />
          <FeedbackBlock icon="thumb_up" title="강점" items={feedback.strengths} />
          <FeedbackBlock icon="build" title="보완점" items={feedback.improvements} />
          {feedback.suggestedAnswer && (
            <div className="rounded-lg border border-border bg-background p-4">
              <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
                <span className="material-symbols-outlined text-[18px] text-primary">
                  edit_note
                </span>
                개선 예시
              </div>
              <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-muted-foreground">
                {feedback.suggestedAnswer}
              </p>
            </div>
          )}
          <FeedbackBlock icon="forum" title="예상 꼬리질문" items={feedback.followups} />
        </div>
      )}
    </article>
  );
}

function FeedbackBlock({
  icon,
  title,
  items,
}: {
  icon: string;
  title: string;
  items: string[];
}) {
  if (!items.length) return null;
  return (
    <div className="rounded-lg border border-border bg-background p-4">
      <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
        <span className="material-symbols-outlined text-[18px] text-primary">{icon}</span>
        {title}
      </div>
      <ul className="mt-2 space-y-1.5">
        {items.map((item, index) => (
          <li key={`${title}-${index}`} className="text-sm leading-6 text-muted-foreground">
            {item}
          </li>
        ))}
      </ul>
    </div>
  );
}
