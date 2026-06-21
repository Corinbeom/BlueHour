"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import Image from "next/image";
import { useMemo, useState } from "react";
import {
  createRecruitmentEntry,
  deleteRecruitmentEntry,
  updateRecruitmentEntry,
  updateRecruitmentEntryStep,
} from "../api/recruitmentEntryApi";
import {
  createRecruitmentEntryNote,
  deleteRecruitmentEntryNote,
} from "../api/recruitmentEntryNoteApi";
import type {
  PlatformType,
  RecruitmentEntry,
  RecruitmentStep,
} from "../api/types";
import { useRecruitmentEntries } from "../hooks/useRecruitmentEntries";
import { useRecruitmentEntryNotes } from "../hooks/useRecruitmentEntryNotes";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

type ColumnKey = "APPLIED" | "IN_REVIEW" | "INTERVIEWING" | "FINAL";

function todayLocalISODate() {
  const d = new Date();
  const local = new Date(d.getTime() - d.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}

function platformLabel(p: PlatformType) {
  switch (p) {
    case "MANUAL": return "기업 사이트 지원";
    case "WANTED": return "원티드";
    case "LINKEDIN": return "LinkedIn";
    case "JOBKOREA": return "잡코리아";
    case "SARMIN": return "사람인";
    case "REMEMBER": return "리멤버";
    case "JUMPIT": return "점핏";
    case "ROCKETPUNCH": return "로켓펀치";
    case "PROGRAMMERS": return "프로그래머스";
    case "ETC": return "기타";
    case "GROUPBY": return "그룹바이";
  }
}

const ALL_PLATFORMS: PlatformType[] = [
  "MANUAL", "WANTED", "LINKEDIN", "JOBKOREA", "SARMIN",
  "REMEMBER", "JUMPIT", "ROCKETPUNCH", "PROGRAMMERS", "GROUPBY", "ETC",
];

const JOB_PLATFORMS = [
  { key: "wanted", name: "원티드", url: "https://www.wanted.co.kr", logo: "/logos/wanted.png" },
  { key: "linkedin", name: "LinkedIn", url: "https://www.linkedin.com/jobs", logo: "/logos/linkedin.png" },
  { key: "jobkorea", name: "잡코리아", url: "https://www.jobkorea.co.kr", logo: "/logos/jobkorea.svg" },
  { key: "saramin", name: "사람인", url: "https://www.saramin.co.kr", logo: "/logos/saramin.svg" },
  { key: "remember", name: "리멤버", url: "https://rememberapp.co.kr", logo: "/logos/remember.png" },
  { key: "jumpit", name: "점핏", url: "https://www.jumpit.co.kr", logo: "/logos/jumpit.webp" },
  { key: "groupby", name: "그룹바이", url: "https://www.groupby.kr", logo: "/logos/groupby.jpeg" },
];

function mapStepToColumn(step: RecruitmentStep): ColumnKey {
  switch (step) {
    case "READY":
    case "APPLIED": return "APPLIED";
    case "DOC_PASSED":
    case "TEST_PHASE": return "IN_REVIEW";
    case "INTERVIEWING": return "INTERVIEWING";
    case "OFFERED":
    case "REJECTED": return "FINAL";
  }
}

const COLUMN_META: Record<ColumnKey, { title: string; dotColor: string; borderColor: string }> = {
  APPLIED:     { title: "지원 완료", dotColor: "bg-primary",      borderColor: "border-l-primary" },
  IN_REVIEW:   { title: "전형 진행", dotColor: "bg-blue-500",     borderColor: "border-l-blue-500" },
  INTERVIEWING:{ title: "면접",     dotColor: "bg-amber-500",    borderColor: "border-l-amber-500" },
  FINAL:       { title: "결과",     dotColor: "bg-emerald-500",  borderColor: "border-l-emerald-500" },
};

function defaultStepForColumn(key: ColumnKey): RecruitmentStep {
  switch (key) {
    case "APPLIED": return "APPLIED";
    case "IN_REVIEW": return "DOC_PASSED";
    case "INTERVIEWING": return "INTERVIEWING";
    case "FINAL": return "OFFERED";
  }
}

function toKoreanStep(step: RecruitmentStep) {
  switch (step) {
    case "READY": return "준비";
    case "APPLIED": return "지원";
    case "DOC_PASSED": return "서류 합격";
    case "TEST_PHASE": return "테스트";
    case "INTERVIEWING": return "면접";
    case "OFFERED": return "최종 합격";
    case "REJECTED": return "불합격";
  }
}

function stepBorderColor(step: RecruitmentStep) {
  switch (step) {
    case "OFFERED": return "border-l-teal";
    case "INTERVIEWING": return "border-l-amber-500";
    case "REJECTED": return "border-l-destructive";
    default: return "border-l-primary";
  }
}

export function ApplicationTrackerView() {
  const qc = useQueryClient();
  const { data: entries = [], isLoading, error } = useRecruitmentEntries();
  const [dragOver, setDragOver] = useState<ColumnKey | null>(null);
  const [selected, setSelected] = useState<RecruitmentEntry | null>(null);
  const [isAddOpen, setIsAddOpen] = useState(false);
  const [isPlatformsOpen, setIsPlatformsOpen] = useState(false);

  const grouped = useMemo(() => {
    const init: Record<ColumnKey, RecruitmentEntry[]> = {
      APPLIED: [], IN_REVIEW: [], INTERVIEWING: [], FINAL: [],
    };
    for (const e of entries) init[mapStepToColumn(e.step)].push(e);
    return init;
  }, [entries]);

  const createMutation = useMutation({
    mutationFn: async (input: {
      companyName: string;
      position: string;
      appliedDate: string | null;
      platformType: PlatformType;
    }) => {
      return await createRecruitmentEntry({
        companyName: input.companyName.trim(),
        position: input.position.trim(),
        step: "APPLIED",
        appliedDate: input.appliedDate,
        platformType: input.platformType,
      });
    },
    onSuccess: async () => {
      setIsAddOpen(false);
      await qc.invalidateQueries({ queryKey: ["recruitmentEntries"] });
    },
  });

  const stepMutation = useMutation({
    mutationFn: async (input: { id: number; step: RecruitmentStep }) => {
      return await updateRecruitmentEntryStep(input);
    },
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["recruitmentEntries"] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: async (id: number) => {
      await deleteRecruitmentEntry(id);
    },
    onSuccess: async () => {
      setSelected(null);
      await qc.invalidateQueries({ queryKey: ["recruitmentEntries"] });
    },
  });

  function handleDrop(column: ColumnKey, entryId: number) {
    stepMutation.mutate({ id: entryId, step: defaultStepForColumn(column) });
  }

  return (
    <div className="flex flex-col gap-6">
      {/* ── Hero ── */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-[22px] font-bold text-foreground">지원 현황</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            드래그로 단계를 옮기고, 카드 클릭으로 상세를 편집할 수 있어요.
          </p>
        </div>
        <button
          type="button"
          onClick={() => { createMutation.reset(); setIsAddOpen(true); }}
          className="inline-flex shrink-0 items-center gap-2 rounded-xl bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition-all hover:bg-primary/90"
        >
          <span className="material-symbols-outlined text-sm">add_circle</span>
          지원 추가
        </button>
      </div>

      {error && (
        <div className="flex items-center gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-4">
          <span className="material-symbols-outlined text-destructive">error</span>
          <p className="text-sm text-destructive">
            목록 조회 오류: {error instanceof Error ? error.message : "알 수 없음"}
          </p>
        </div>
      )}

      {/* ── Stats strip ── */}
      <div className="grid grid-cols-3 gap-3">
        {[
          { label: "총 지원", value: entries.length, icon: "description", iconCls: "text-primary bg-primary/10" },
          { label: "면접", value: grouped.INTERVIEWING.length, icon: "event", iconCls: "text-amber-600 bg-amber-500/10" },
          { label: "오퍼", value: entries.filter((e) => e.step === "OFFERED").length, icon: "workspace_premium", iconCls: "text-teal bg-teal/10" },
        ].map((stat) => (
          <div key={stat.label} className="flex items-center gap-3 rounded-xl border border-border bg-card p-4">
            <div className={cn("flex size-10 shrink-0 items-center justify-center rounded-lg", stat.iconCls)}>
              <span className="material-symbols-outlined text-[20px]">{stat.icon}</span>
            </div>
            <div>
              <p className="text-xl font-bold leading-none text-foreground">{stat.value}</p>
              <p className="mt-1 text-xs text-muted-foreground">{stat.label}</p>
            </div>
          </div>
        ))}
      </div>

      {/* ── 지원하러 가기 ── */}
      <div className="overflow-hidden rounded-xl border border-border bg-card">
        <button
          type="button"
          onClick={() => setIsPlatformsOpen((prev) => !prev)}
          className="flex w-full items-center justify-between px-5 py-3.5"
        >
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-sm text-primary">link</span>
            <span className="text-sm font-semibold text-foreground">지원하러 가기</span>
          </div>
          <span
            className={cn(
              "material-symbols-outlined text-xl text-muted-foreground transition-transform",
              isPlatformsOpen && "rotate-180"
            )}
          >
            expand_more
          </span>
        </button>
        {isPlatformsOpen && (
          <div className="grid grid-cols-4 gap-3 border-t border-border px-5 py-4 sm:grid-cols-7">
            {JOB_PLATFORMS.map((platform) => (
              <a
                key={platform.key}
                href={platform.url}
                target="_blank"
                rel="noopener noreferrer"
                className="group flex flex-col items-center gap-2 rounded-lg p-2 transition-all hover:-translate-y-0.5 hover:bg-muted/50"
              >
                <PlatformLogo src={platform.logo} name={platform.name} platformKey={platform.key} />
                <span className="text-xs font-medium text-muted-foreground transition-colors group-hover:text-foreground">
                  {platform.name}
                </span>
              </a>
            ))}
          </div>
        )}
      </div>

      {/* ── Kanban board ── */}
      <section className="grid grid-cols-1 items-start gap-5 md:grid-cols-2 xl:grid-cols-4">
        {(["APPLIED", "IN_REVIEW", "INTERVIEWING", "FINAL"] as const).map((key) => (
          <KanbanColumn
            key={key}
            columnKey={key}
            count={grouped[key].length}
            isDragOver={dragOver === key}
            onDragOver={() => setDragOver(key)}
            onDragLeave={() => setDragOver(null)}
            onDrop={(entryId) => { setDragOver(null); handleDrop(key, entryId); }}
          >
            {isLoading ? (
              <div className="flex h-16 items-center justify-center rounded-xl border border-dashed border-border">
                <p className="text-xs text-muted-foreground">불러오는 중...</p>
              </div>
            ) : grouped[key].length === 0 ? (
              <EmptySlot />
            ) : (
              grouped[key].map((e) => (
                <KanbanCard
                  key={e.id}
                  id={e.id}
                  company={e.companyName}
                  role={e.position}
                  step={e.step}
                  appliedDate={e.appliedDate ?? null}
                  onStepChange={(step) => stepMutation.mutate({ id: e.id, step })}
                  onDragStart={(id) => void id}
                  onOpenDetails={() => {
                    deleteMutation.reset();
                    setSelected(e);
                  }}
                />
              ))
            )}
          </KanbanColumn>
        ))}
      </section>

      <EntryDetailsModal
        key={selected?.id ?? "details-closed"}
        open={selected != null}
        entry={selected}
        onClose={() => {
          deleteMutation.reset();
          setSelected(null);
        }}
        onStepChange={(id, step) => {
          setSelected((prev) => (prev ? { ...prev, step } : prev));
          stepMutation.mutate({ id, step });
        }}
        onSave={async (payload) => {
          const updated = await updateRecruitmentEntry(payload);
          await qc.invalidateQueries({ queryKey: ["recruitmentEntries"] });
          setSelected(updated);
        }}
        onDelete={(id) => {
          const target = entries.find((e) => e.id === id) ?? selected;
          const label = target ? `${target.companyName} ${target.position}` : "이 지원";
          if (!window.confirm(`"${label}" 항목을 삭제하시겠습니까?`)) return;
          deleteMutation.mutate(id);
        }}
        isDeleting={deleteMutation.isPending}
        deleteError={deleteMutation.error instanceof Error ? deleteMutation.error.message : null}
      />

      <AddEntryModal
        key={isAddOpen ? "add-open" : "add-closed"}
        open={isAddOpen}
        onClose={() => { createMutation.reset(); setIsAddOpen(false); }}
        isSubmitting={createMutation.isPending}
        errorMessage={
          createMutation.error instanceof Error ? createMutation.error.message : null
        }
        onSubmit={(payload) => createMutation.mutate(payload)}
      />
    </div>
  );
}

// ─── KanbanColumn ───
function KanbanColumn({
  columnKey,
  count,
  children,
  isDragOver,
  onDragOver,
  onDragLeave,
  onDrop,
}: {
  columnKey: ColumnKey;
  count: number;
  children: React.ReactNode;
  isDragOver: boolean;
  onDragOver: () => void;
  onDragLeave: () => void;
  onDrop: (entryId: number) => void;
}) {
  const meta = COLUMN_META[columnKey];
  return (
    <div className="flex min-h-[480px] flex-col gap-3">
      {/* Column header */}
      <div className="flex items-center gap-2 px-1">
        <div className={cn("size-2 rounded-full", meta.dotColor)} />
        <span className="text-sm font-semibold text-foreground">{meta.title}</span>
        <span className="ml-auto text-xs text-muted-foreground">{count}</span>
      </div>

      {/* Drop zone */}
      <div
        className={cn(
          "flex flex-1 flex-col gap-2.5 rounded-xl p-1 transition-all",
          isDragOver && "bg-primary/5 ring-2 ring-primary/20"
        )}
        onDragOver={(e) => { e.preventDefault(); onDragOver(); }}
        onDragLeave={onDragLeave}
        onDrop={(e) => {
          e.preventDefault();
          const raw = e.dataTransfer.getData("text/bluehour-recruitment-entry-id");
          const id = Number(raw);
          if (!Number.isNaN(id) && id > 0) onDrop(id);
        }}
      >
        {children}
      </div>
    </div>
  );
}

// ─── KanbanCard ───
function KanbanCard({
  id,
  company,
  role,
  step,
  appliedDate,
  onStepChange,
  onDragStart,
  onOpenDetails,
}: {
  id: number;
  company: string;
  role: string;
  step: RecruitmentStep;
  appliedDate: string | null;
  onStepChange: (step: RecruitmentStep) => void;
  onDragStart: (id: number) => void;
  onOpenDetails: () => void;
}) {
  return (
    <div
      className={cn(
        "group cursor-grab overflow-hidden rounded-xl border border-border bg-card transition-colors hover:bg-accent/30",
        "border-l-[3px]",
        stepBorderColor(step)
      )}
      draggable
      role="button"
      tabIndex={0}
      onClick={onOpenDetails}
      onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") onOpenDetails(); }}
      onDragStart={(e) => {
        e.dataTransfer.setData("text/bluehour-recruitment-entry-id", String(id));
        onDragStart(id);
      }}
    >
      <div className="flex flex-col gap-2.5 px-4 py-3.5">
        {/* Company + role */}
        <div>
          <p className="text-[15px] font-semibold leading-tight text-foreground">{company}</p>
          <p className="mt-0.5 text-xs text-muted-foreground">{role}</p>
        </div>

        {/* Applied date */}
        {appliedDate && (
          <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
            <span className="material-symbols-outlined text-sm">calendar_today</span>
            {appliedDate}
          </div>
        )}

        {/* Step selector + notes */}
        <div className="flex items-center justify-between gap-2">
          <select
            value={step}
            onChange={(e) => onStepChange(e.target.value as RecruitmentStep)}
            onPointerDown={(e) => e.stopPropagation()}
            onMouseDown={(e) => e.stopPropagation()}
            onClick={(e) => e.stopPropagation()}
            className="h-7 flex-1 rounded-full border border-border bg-background px-2.5 text-[12px] font-medium text-foreground outline-none focus:border-primary focus:ring-1 focus:ring-ring"
          >
            <option value="READY">준비</option>
            <option value="APPLIED">지원</option>
            <option value="DOC_PASSED">서류 합격</option>
            <option value="TEST_PHASE">테스트</option>
            <option value="INTERVIEWING">면접</option>
            <option value="OFFERED">최종 합격</option>
            <option value="REJECTED">불합격</option>
          </select>
          <button
            type="button"
            onClick={(e) => e.stopPropagation()}
            className="flex items-center text-muted-foreground/50 transition-colors hover:text-muted-foreground"
            aria-label="메모"
          >
            <span className="material-symbols-outlined text-[18px]">sticky_note_2</span>
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── EntryDetailsModal ───
function EntryDetailsModal({
  open,
  entry,
  onClose,
  onStepChange,
  onSave,
  onDelete,
  isDeleting,
  deleteError,
}: {
  open: boolean;
  entry: RecruitmentEntry | null;
  onClose: () => void;
  onStepChange: (id: number, step: RecruitmentStep) => void;
  onSave: (payload: {
    id: number;
    companyName: string;
    position: string;
    step: RecruitmentStep;
    platformType: PlatformType;
    externalId: string | null;
    appliedDate: string | null;
  }) => Promise<void>;
  onDelete: (id: number) => void;
  isDeleting: boolean;
  deleteError: string | null;
}) {
  const qc = useQueryClient();
  const [companyName, setCompanyName] = useState(entry?.companyName ?? "");
  const [position, setPosition] = useState(entry?.position ?? "");
  const [appliedDate, setAppliedDate] = useState(entry?.appliedDate ?? "");
  const [externalId, setExternalId] = useState(entry?.externalId ?? "");
  const [platformType, setPlatformType] = useState<PlatformType>(entry?.platformType ?? "MANUAL");
  const [justSaved, setJustSaved] = useState(false);
  const [newNote, setNewNote] = useState("");

  const entryId = entry?.id ?? null;
  const notesQuery = useRecruitmentEntryNotes(entryId, open);

  const createNoteMutation = useMutation({
    mutationFn: async () => {
      if (!entry) throw new Error("entry가 없습니다.");
      if (!newNote.trim()) throw new Error("메모를 입력해 주세요.");
      return await createRecruitmentEntryNote(entry.id, newNote.trim());
    },
    onSuccess: async () => {
      setNewNote("");
      await qc.invalidateQueries({ queryKey: ["recruitmentEntryNotes", entryId] });
    },
  });

  const deleteNoteMutation = useMutation({
    mutationFn: async (noteId: number) => {
      if (!entry) throw new Error("entry가 없습니다.");
      await deleteRecruitmentEntryNote(entry.id, noteId);
    },
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["recruitmentEntryNotes", entryId] });
    },
  });

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!entry) throw new Error("entry가 없습니다.");
      if (!companyName.trim() || !position.trim()) throw new Error("회사/포지션은 필수입니다.");
      const minDelayMs = 400;
      const startedAt = Date.now();
      await onSave({
        id: entry.id,
        companyName: companyName.trim(),
        position: position.trim(),
        step: entry.step,
        platformType,
        externalId: externalId.trim() ? externalId.trim() : null,
        appliedDate: appliedDate ? appliedDate : null,
      });
      const elapsed = Date.now() - startedAt;
      if (elapsed < minDelayMs) {
        await new Promise((r) => setTimeout(r, minDelayMs - elapsed));
      }
    },
    onSuccess: () => {
      setJustSaved(true);
      window.setTimeout(() => setJustSaved(false), 2000);
    },
  });

  if (!open || !entry) return null;
  const notes = notesQuery.data ?? [];

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center"
      role="dialog"
      aria-modal="true"
      onMouseDown={onClose}
    >
      <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" />
      <div
        className="relative z-10 max-h-[90vh] w-[min(560px,calc(100%-24px))] overflow-y-auto rounded-2xl border border-border bg-background p-6 shadow-2xl"
        onMouseDown={(e) => e.stopPropagation()}
      >
        {/* Modal header */}
        <div className="mb-5 flex items-start justify-between gap-4">
          <div className="flex-1 min-w-0">
            <p className="mb-2 font-mono text-[10px] uppercase tracking-widest text-muted-foreground">
              지원 상세
            </p>
            <div className="flex flex-col gap-2">
              <Input
                value={companyName}
                onChange={(e) => setCompanyName(e.target.value)}
                placeholder="회사명"
                aria-label="회사명"
                className="text-[15px] font-bold"
              />
              <Input
                value={position}
                onChange={(e) => setPosition(e.target.value)}
                placeholder="포지션"
                aria-label="포지션"
              />
            </div>
          </div>
          <Button variant="ghost" size="icon" onClick={onClose} aria-label="닫기">
            <span className="material-symbols-outlined">close</span>
          </Button>
        </div>

        {justSaved && (
          <div className="mb-4 flex items-center gap-2 rounded-xl border border-teal/30 bg-teal/10 px-4 py-3 text-sm font-semibold text-teal">
            <span className="material-symbols-outlined text-lg">check_circle</span>
            저장 완료
          </div>
        )}

        {/* 2x2 info grid */}
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div className="rounded-xl border border-border bg-card p-4">
            <p className="mb-2 font-mono text-[10px] uppercase tracking-widest text-muted-foreground">지원일</p>
            <input
              type="date"
              value={appliedDate}
              max={todayLocalISODate()}
              onChange={(e) => setAppliedDate(e.target.value)}
              className="h-9 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none focus:border-primary focus:ring-1 focus:ring-ring"
              aria-label="지원일"
            />
          </div>

          <div className="rounded-xl border border-border bg-card p-4">
            <p className="mb-2 font-mono text-[10px] uppercase tracking-widest text-muted-foreground">현재 단계</p>
            <div className="flex items-center gap-2">
              <span className="material-symbols-outlined text-lg text-muted-foreground">timeline</span>
              <p className="text-sm font-bold text-foreground">{toKoreanStep(entry.step)}</p>
            </div>
          </div>

          <div className="rounded-xl border border-border bg-card p-4">
            <p className="mb-2 font-mono text-[10px] uppercase tracking-widest text-muted-foreground">플랫폼</p>
            <select
              value={platformType}
              onChange={(e) => setPlatformType(e.target.value as PlatformType)}
              className="h-9 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none focus:border-primary focus:ring-1 focus:ring-ring"
              aria-label="플랫폼"
            >
              {ALL_PLATFORMS.map((p) => (
                <option key={p} value={p}>{platformLabel(p)}</option>
              ))}
            </select>
          </div>

          <div className="rounded-xl border border-border bg-card p-4">
            <p className="mb-2 font-mono text-[10px] uppercase tracking-widest text-muted-foreground">외부 ID</p>
            <Input
              value={externalId}
              onChange={(e) => setExternalId(e.target.value)}
              placeholder="원티드/링크드인 등"
              aria-label="외부 ID"
            />
          </div>
        </div>

        {/* 단계 변경 */}
        <div className="mt-4 rounded-xl border border-border bg-card p-4">
          <p className="mb-2 font-mono text-[10px] uppercase tracking-widest text-muted-foreground">단계 변경</p>
          <select
            value={entry.step}
            onChange={(e) => onStepChange(entry.id, e.target.value as RecruitmentStep)}
            className="h-9 w-full rounded-lg border border-input bg-background px-3 text-sm font-medium text-foreground outline-none focus:border-primary focus:ring-1 focus:ring-ring"
          >
            <option value="READY">준비</option>
            <option value="APPLIED">지원</option>
            <option value="DOC_PASSED">서류 합격</option>
            <option value="TEST_PHASE">테스트</option>
            <option value="INTERVIEWING">면접</option>
            <option value="OFFERED">최종 합격</option>
            <option value="REJECTED">불합격</option>
          </select>
          <p className="mt-2 text-xs text-muted-foreground">
            카드 드래그로도 이동할 수 있어요.
          </p>
        </div>

        {/* 메모 */}
        <div className="mt-4 rounded-xl border border-border bg-card p-4">
          <div className="mb-3 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="material-symbols-outlined text-lg text-muted-foreground">sticky_note_2</span>
              <p className="font-mono text-[10px] uppercase tracking-widest text-muted-foreground">메모</p>
            </div>
            <span className="text-xs text-muted-foreground">
              {notesQuery.isLoading ? "..." : `${notes.length}개`}
            </span>
          </div>

          <div className="flex gap-2">
            <Input
              value={newNote}
              onChange={(e) => setNewNote(e.target.value)}
              placeholder="메모를 입력해 주세요"
            />
            <Button size="sm" disabled={createNoteMutation.isPending} onClick={() => createNoteMutation.mutate()}>
              추가
            </Button>
          </div>
          {createNoteMutation.error && (
            <p className="mt-2 text-sm text-destructive">
              메모 오류:{" "}
              {createNoteMutation.error instanceof Error ? createNoteMutation.error.message : "알 수 없음"}
            </p>
          )}

          <div className="mt-3 flex flex-col gap-2">
            {notesQuery.isLoading ? (
              <div className="flex h-12 items-center justify-center rounded-lg border border-dashed border-border">
                <p className="text-xs text-muted-foreground">메모 불러오는 중...</p>
              </div>
            ) : notesQuery.error ? (
              <div className="rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
                메모 조회 오류:{" "}
                {notesQuery.error instanceof Error ? notesQuery.error.message : "알 수 없음"}
              </div>
            ) : notes.length === 0 ? (
              <div className="flex h-12 items-center justify-center rounded-lg border border-dashed border-border">
                <p className="text-xs text-muted-foreground">아직 메모가 없어요.</p>
              </div>
            ) : (
              notes.map((n) => (
                <div
                  key={n.id}
                  className="flex items-start justify-between gap-3 rounded-lg border border-border bg-background p-3"
                >
                  <div className="min-w-0">
                    <p className="whitespace-pre-wrap break-words text-sm text-foreground">{n.content}</p>
                    <p className="mt-1 text-[11px] text-muted-foreground">
                      {n.createdAt ? n.createdAt.slice(0, 19).replace("T", " ") : ""}
                    </p>
                  </div>
                  <Button
                    variant="ghost"
                    size="icon"
                    onClick={() => deleteNoteMutation.mutate(n.id)}
                    disabled={deleteNoteMutation.isPending}
                    className="shrink-0 text-muted-foreground hover:text-destructive"
                    aria-label="메모 삭제"
                  >
                    <span className="material-symbols-outlined text-lg">delete</span>
                  </Button>
                </div>
              ))
            )}
          </div>
        </div>

        {/* 저장/삭제/닫기 */}
        <div className="mt-5 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <Button
            variant="outline"
            onClick={() => onDelete(entry.id)}
            disabled={isDeleting || saveMutation.isPending}
            className="gap-2 border-destructive/30 text-destructive hover:bg-destructive/10 hover:text-destructive"
          >
            <span className="material-symbols-outlined text-sm">
              {isDeleting ? "progress_activity" : "delete"}
            </span>
            {isDeleting ? "삭제 중..." : "지원 삭제"}
          </Button>
          <div className="flex items-center justify-end gap-2">
            <Button variant="outline" onClick={onClose} disabled={isDeleting}>닫기</Button>
            <Button onClick={() => saveMutation.mutate()} disabled={saveMutation.isPending || isDeleting} className="gap-2">
            {saveMutation.isPending ? (
              <>
                <span className="material-symbols-outlined animate-spin text-lg">progress_activity</span>
                저장 중...
              </>
            ) : "저장"}
            </Button>
          </div>
        </div>

        {saveMutation.error && (
          <p className="mt-3 text-sm text-destructive">
            저장 오류:{" "}
            {saveMutation.error instanceof Error ? saveMutation.error.message : "알 수 없음"}
          </p>
        )}
        {deleteError && (
          <p className="mt-3 text-sm text-destructive">삭제 오류: {deleteError}</p>
        )}
      </div>
    </div>
  );
}

// ─── AddEntryModal ───
function AddEntryModal({
  open,
  onClose,
  onSubmit,
  isSubmitting,
  errorMessage,
}: {
  open: boolean;
  onClose: () => void;
  onSubmit: (payload: {
    companyName: string;
    position: string;
    appliedDate: string | null;
    platformType: PlatformType;
  }) => void;
  isSubmitting: boolean;
  errorMessage: string | null;
}) {
  const [companyName, setCompanyName] = useState("");
  const [position, setPosition] = useState("");
  const [appliedDate, setAppliedDate] = useState(todayLocalISODate());
  const [platformType, setPlatformType] = useState<PlatformType>("MANUAL");

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center"
      role="dialog"
      aria-modal="true"
      onMouseDown={onClose}
    >
      <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" />
      <div
        className="relative z-10 w-[min(560px,calc(100%-24px))] rounded-2xl border border-border bg-background p-6 shadow-2xl"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            <p className="mb-1 font-mono text-[10px] uppercase tracking-widest text-muted-foreground">
              지원 추가
            </p>
            <h3 className="text-xl font-bold text-foreground">새로운 지원을 기록해요</h3>
            <p className="mt-1 text-sm text-muted-foreground">
              저장 후에는 카드에서 단계 이동/상세 편집이 가능해요.
            </p>
          </div>
          <Button variant="ghost" size="icon" onClick={onClose} aria-label="닫기">
            <span className="material-symbols-outlined">close</span>
          </Button>
        </div>

        <div className="flex flex-col gap-4">
          {[
            { label: "회사명", placeholder: "예: 네이버", value: companyName, onChange: setCompanyName },
            { label: "포지션", placeholder: "예: 백엔드 엔지니어", value: position, onChange: setPosition },
          ].map(({ label, placeholder, value, onChange }) => (
            <div key={label}>
              <p className="mb-1.5 font-mono text-[10px] uppercase tracking-widest text-muted-foreground">
                {label}
              </p>
              <Input value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} />
            </div>
          ))}

          <div>
            <p className="mb-1.5 font-mono text-[10px] uppercase tracking-widest text-muted-foreground">
              지원일
            </p>
            <input
              type="date"
              value={appliedDate}
              max={todayLocalISODate()}
              onChange={(e) => setAppliedDate(e.target.value)}
              className="h-9 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none focus:border-primary focus:ring-1 focus:ring-ring"
            />
          </div>

          <div>
            <p className="mb-1.5 font-mono text-[10px] uppercase tracking-widest text-muted-foreground">
              지원 플랫폼
            </p>
            <select
              value={platformType}
              onChange={(e) => setPlatformType(e.target.value as PlatformType)}
              className="h-9 w-full rounded-lg border border-input bg-background px-3 text-sm text-foreground outline-none focus:border-primary focus:ring-1 focus:ring-ring"
            >
              {ALL_PLATFORMS.map((p) => (
                <option key={p} value={p}>{platformLabel(p)}</option>
              ))}
            </select>
          </div>
        </div>

        {errorMessage && (
          <p className="mt-3 text-sm text-destructive">생성 오류: {errorMessage}</p>
        )}

        <div className="mt-5 flex items-center justify-end gap-2">
          <Button variant="outline" onClick={onClose}>취소</Button>
          <Button
            disabled={isSubmitting}
            onClick={() => onSubmit({ companyName, position, appliedDate: appliedDate || null, platformType })}
          >
            저장
          </Button>
        </div>
      </div>
    </div>
  );
}

// ─── PlatformLogo ───
function PlatformLogo({ src, name }: { src: string; name: string; platformKey: string }) {
  const [hasError, setHasError] = useState(false);
  if (hasError) {
    return (
      <div className="flex size-10 items-center justify-center rounded-full bg-muted text-sm font-bold text-muted-foreground">
        {name.charAt(0).toUpperCase()}
      </div>
    );
  }
  return (
    <div className="flex size-10 items-center justify-center">
      <Image
        src={src}
        alt={`${name} 로고`}
        width={40}
        height={40}
        className="size-10 object-contain"
        onError={() => setHasError(true)}
      />
    </div>
  );
}

// ─── EmptySlot ───
function EmptySlot() {
  return (
    <div className="flex h-20 items-center justify-center rounded-xl border border-dashed border-border">
      <p className="text-xs text-muted-foreground/40">비어있음</p>
    </div>
  );
}
