"use client";

import { useEffect, useRef, useState } from "react";
import { useAuth } from "@/features/auth/hooks/useAuth";
import {
  useResumeFiles,
  useUploadResumeFile,
  useDeleteResumeFile,
} from "../hooks/useResumeFiles";
import { useDeleteAccount } from "@/features/auth/hooks/useDeleteAccount";
import { updateMyTargetRoles } from "@/features/member/api/memberApi";
import { TargetRoleSelector } from "@/features/member/components/TargetRoleSelector";
import { fetchResumeFileBlob } from "../api/resumeFileApi";
import type { ResumeFile, ResumeFileType } from "../api/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { cn } from "@/lib/utils";
import { toast } from "sonner";
import { InterviewMailScheduleCard } from "./InterviewMailScheduleCard";

function formatBytes(bytes: number | null): string {
  if (bytes == null) return "-";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function extractStatusDot(status: string) {
  switch (status) {
    case "EXTRACTED":
      return { color: "bg-teal", label: "추출 완료" };
    case "FAILED":
      return { color: "bg-destructive", label: "추출 실패" };
    default:
      return { color: "bg-amber-500", label: "추출 대기" };
  }
}

function FilePreviewModal({
  file,
  onClose,
}: {
  file: ResumeFile;
  onClose: () => void;
}) {
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const isPdf = file.contentType === "application/pdf";

  useEffect(() => {
    let url = "";
    fetchResumeFileBlob(file.id)
      .then((blob) => {
        url = URL.createObjectURL(blob);
        setBlobUrl(url);
        setLoading(false);
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : "파일을 불러올 수 없습니다.");
        setLoading(false);
      });
    return () => {
      if (url) URL.revokeObjectURL(url);
    };
  }, [file.id]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black/60 backdrop-blur-sm"
        onClick={onClose}
      />
      {/* Modal */}
      <div className="relative z-10 flex h-[90vh] w-full max-w-4xl flex-col overflow-hidden rounded-2xl border border-border bg-card shadow-2xl">
        {/* Header */}
        <div className="flex shrink-0 items-center gap-3 border-b border-border px-5 py-3">
          <span className="material-symbols-outlined text-[18px] text-muted-foreground">
            {file.fileType === "RESUME" ? "description" : "folder_open"}
          </span>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold text-foreground">{file.title}</p>
            <p className="truncate text-xs text-muted-foreground">{file.originalFilename}</p>
          </div>
          {blobUrl && (
            <a
              href={blobUrl}
              download={file.originalFilename ?? "file"}
              className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            >
              <span className="material-symbols-outlined text-sm">download</span>
              다운로드
            </a>
          )}
          <Button
            variant="ghost"
            size="icon"
            onClick={onClose}
            className="size-7 shrink-0"
            aria-label="닫기"
          >
            <span className="material-symbols-outlined text-sm">close</span>
          </Button>
        </div>

        {/* Content */}
        <div className="flex flex-1 overflow-hidden">
          {loading ? (
            <div className="flex flex-1 items-center justify-center gap-2 text-sm text-muted-foreground">
              <span className="material-symbols-outlined animate-spin">progress_activity</span>
              파일 불러오는 중...
            </div>
          ) : error ? (
            <div className="flex flex-1 items-center justify-center p-6">
              <p className="text-sm text-destructive">{error}</p>
            </div>
          ) : isPdf ? (
            <iframe
              src={blobUrl!}
              className="h-full w-full"
              title={file.title}
            />
          ) : (
            <div className="flex flex-1 flex-col items-center justify-center gap-4 p-8">
              <span className="material-symbols-outlined text-5xl text-muted-foreground/40">
                description
              </span>
              <p className="text-sm text-muted-foreground">
                이 파일 형식은 미리보기를 지원하지 않습니다.
              </p>
              {blobUrl && (
                <a
                  href={blobUrl}
                  download={file.originalFilename ?? "file"}
                  className="inline-flex items-center gap-2 rounded-xl bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:bg-primary/90"
                >
                  <span className="material-symbols-outlined text-sm">download</span>
                  파일 다운로드
                </a>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function FileCard({
  file,
  onDelete,
  isDeleting,
  onPreview,
}: {
  file: ResumeFile;
  onDelete: () => void;
  isDeleting: boolean;
  onPreview: () => void;
}) {
  const dot = extractStatusDot(file.extractStatus);
  const isResume = file.fileType === "RESUME";

  return (
    <div
      className={cn(
        "group flex items-center gap-3 overflow-hidden rounded-xl border border-border bg-background px-4 py-3 transition-colors hover:bg-accent/30",
        "border-l-[3px]",
        isResume ? "border-l-primary" : "border-l-violet-500"
      )}
    >
      <div
        className={cn(
          "flex size-8 shrink-0 items-center justify-center rounded-lg",
          isResume ? "bg-primary/10 text-primary" : "bg-violet-500/10 text-violet-600"
        )}
      >
        <span className="material-symbols-outlined text-[16px]">
          {isResume ? "description" : "folder_open"}
        </span>
      </div>

      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold text-foreground">{file.title}</p>
        <p className="truncate text-xs text-muted-foreground">
          {file.originalFilename} · {formatBytes(file.sizeBytes)}
        </p>
        <div className="mt-0.5 flex items-center gap-1.5">
          <div className={cn("size-1.5 rounded-full", dot.color)} />
          <span className="text-xs text-muted-foreground">{dot.label}</span>
        </div>
      </div>

      <div className="flex shrink-0 items-center gap-0.5 opacity-0 transition-opacity group-hover:opacity-100">
        <Button
          variant="ghost"
          size="icon"
          onClick={onPreview}
          className="size-7 text-muted-foreground hover:text-foreground"
          aria-label="미리보기"
        >
          <span className="material-symbols-outlined text-sm">visibility</span>
        </Button>
        <Button
          variant="ghost"
          size="icon"
          onClick={onDelete}
          disabled={isDeleting}
          className="size-7 text-muted-foreground hover:text-destructive"
          aria-label="삭제"
        >
          <span className="material-symbols-outlined text-sm">delete</span>
        </Button>
      </div>
    </div>
  );
}

export function ProfileView() {
  const { user, refresh } = useAuth();
  const displayName = user?.displayName ?? user?.email ?? "사용자";
  const initial = displayName.charAt(0).toUpperCase();

  const { data: files, isLoading } = useResumeFiles();
  const uploadMutation = useUploadResumeFile();
  const deleteMutation = useDeleteResumeFile();
  const deleteAccountMutation = useDeleteAccount();

  const [fileTab, setFileTab] = useState<ResumeFileType>("RESUME");
  const [uploadType, setUploadType] = useState<ResumeFileType>("RESUME");
  const [uploadTitle, setUploadTitle] = useState("");
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isDragOver, setIsDragOver] = useState(false);
  const [previewFile, setPreviewFile] = useState<ResumeFile | null>(null);
  const [targetRoles, setTargetRoles] = useState<string[]>(user?.targetRoles ?? []);
  const [isSavingRoles, setIsSavingRoles] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    setTargetRoles(user?.targetRoles ?? []);
  }, [user?.targetRoles]);

  const resumeFiles = files?.filter((f) => f.fileType === "RESUME") ?? [];
  const portfolioFiles = files?.filter((f) => f.fileType === "PORTFOLIO") ?? [];
  const displayFiles = fileTab === "RESUME" ? resumeFiles : portfolioFiles;

  function onDeleteFile(file: ResumeFile) {
    if (!confirm(`정말 "${file.originalFilename}"을(를) 삭제하시겠습니까?`)) return;
    deleteMutation.mutate(file.id, {
      onSuccess: () => toast.success(`"${file.originalFilename}" 삭제가 완료되었습니다.`),
    });
  }

  function onDeleteAccount() {
    if (!user) return;
    if (!confirm("정말 탈퇴를 하시겠습니까? \n탈퇴 시 모든 정보가 삭제되며 복구할 수 없습니다.")) return;
    deleteAccountMutation.mutate(user.id, {
      onError: (err) => toast.error(err instanceof Error ? err.message : String(err)),
    });
  }

  async function onUpload() {
    if (!selectedFile) return;
    await uploadMutation.mutateAsync({
      file: selectedFile,
      fileType: uploadType,
      title: uploadTitle.trim() || undefined,
    });
    setSelectedFile(null);
    setUploadTitle("");
    if (fileInputRef.current) fileInputRef.current.value = "";
  }

  async function onSaveTargetRoles() {
    setIsSavingRoles(true);
    try {
      await updateMyTargetRoles(targetRoles);
      await refresh();
      toast.success("관심 직무가 저장되었습니다.");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "관심 직무 저장에 실패했습니다.");
    } finally {
      setIsSavingRoles(false);
    }
  }

  return (
    <>
    {previewFile && (
      <FilePreviewModal file={previewFile} onClose={() => setPreviewFile(null)} />
    )}
    <div className="flex flex-col gap-5">
      {/* ── Compact Hero ── */}
      <div className="flex items-center gap-4 rounded-xl border border-border bg-card px-5 py-4">
        <Avatar className="size-12 shrink-0 border-2 border-background shadow-sm">
          <AvatarImage src={user?.photoUrl ?? undefined} alt={displayName} />
          <AvatarFallback className="bg-primary text-lg font-bold text-primary-foreground">
            {initial}
          </AvatarFallback>
        </Avatar>
        <div>
          <p className="font-bold text-foreground">{displayName}</p>
          <p className="text-sm text-muted-foreground">{user?.email}</p>
        </div>
      </div>

      {/* ── 2-col main layout ── */}
      <div className="grid grid-cols-1 gap-5 md:grid-cols-[1fr_320px]">

        {/* ── LEFT: File Management ── */}
        <div className="flex flex-col gap-4">

          {/* File list (unified with tab toggle) */}
          <div className="rounded-xl border border-border bg-card p-5">
            <div className="mb-4 flex items-center gap-2.5">
              <span className="text-sm font-bold text-foreground">업로드 파일</span>
              <div className="ml-auto flex gap-1">
                {([
                  { type: "RESUME" as const, label: "이력서", count: resumeFiles.length, activeClass: "bg-primary/10 text-primary" },
                  { type: "PORTFOLIO" as const, label: "포트폴리오", count: portfolioFiles.length, activeClass: "bg-violet-500/10 text-violet-600" },
                ] as const).map((tab) => (
                  <button
                    key={tab.type}
                    onClick={() => setFileTab(tab.type)}
                    className={cn(
                      "flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors",
                      fileTab === tab.type
                        ? tab.activeClass
                        : "text-muted-foreground hover:bg-muted hover:text-foreground"
                    )}
                  >
                    {tab.label}
                    <span className="rounded-full bg-muted px-1.5 py-0.5 font-mono text-[10px] font-bold">
                      {tab.count}
                    </span>
                  </button>
                ))}
              </div>
            </div>

            {isLoading ? (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <span className="material-symbols-outlined animate-spin text-sm">progress_activity</span>
                불러오는 중...
              </div>
            ) : displayFiles.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                {fileTab === "RESUME" ? "아직 업로드한 이력서가 없어요." : "아직 업로드한 포트폴리오가 없어요."}
              </p>
            ) : (
              <div className="flex flex-col gap-2">
                {displayFiles.map((f) => (
                  <FileCard
                    key={f.id}
                    file={f}
                    onDelete={() => onDeleteFile(f)}
                    isDeleting={deleteMutation.isPending}
                    onPreview={() => setPreviewFile(f)}
                  />
                ))}
              </div>
            )}
          </div>

          {/* Upload form (compact) */}
          <div className="rounded-xl border border-border bg-card p-5">
            <p className="mb-4 text-sm font-bold text-foreground">파일 업로드</p>

            <div className="flex flex-col gap-3">
              {/* Type toggle + Title input row */}
              <div className="grid grid-cols-2 gap-3">
                <div className="flex flex-col gap-1.5">
                  <span className="text-xs font-semibold text-muted-foreground">파일 유형</span>
                  <div className="flex gap-1">
                    {(["RESUME", "PORTFOLIO"] as const).map((t) => (
                      <button
                        key={t}
                        onClick={() => setUploadType(t)}
                        className={cn(
                          "rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors",
                          uploadType === t
                            ? t === "RESUME"
                              ? "bg-primary/10 text-primary"
                              : "bg-violet-500/10 text-violet-600"
                            : "text-muted-foreground hover:bg-muted hover:text-foreground"
                        )}
                      >
                        {t === "RESUME" ? "이력서" : "포트폴리오"}
                      </button>
                    ))}
                  </div>
                </div>

                <label className="flex flex-col gap-1.5">
                  <span className="text-xs font-semibold text-muted-foreground">제목 (선택)</span>
                  <Input
                    placeholder="2026 상반기 이력서"
                    value={uploadTitle}
                    onChange={(e) => setUploadTitle(e.target.value)}
                    className="h-8 text-sm"
                  />
                </label>
              </div>

              {/* Compact drag-drop zone */}
              <input
                ref={fileInputRef}
                type="file"
                accept=".pdf,.txt"
                className="hidden"
                onChange={(e) => setSelectedFile(e.target.files?.[0] ?? null)}
              />
              <div
                className={cn(
                  "cursor-pointer rounded-xl border-2 border-dashed px-4 py-3 transition-all",
                  isDragOver
                    ? "border-primary/60 bg-primary/5"
                    : "border-border bg-muted/30 hover:border-primary/40 hover:bg-primary/5"
                )}
                role="button"
                tabIndex={0}
                onClick={() => fileInputRef.current?.click()}
                onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") fileInputRef.current?.click(); }}
                onDragOver={(e) => { e.preventDefault(); setIsDragOver(true); }}
                onDragLeave={() => setIsDragOver(false)}
                onDrop={(e) => {
                  e.preventDefault();
                  setIsDragOver(false);
                  const f = e.dataTransfer.files?.[0] ?? null;
                  if (f) setSelectedFile(f);
                }}
              >
                <div className="flex items-center gap-3">
                  <span className="material-symbols-outlined text-2xl text-muted-foreground/50">
                    cloud_upload
                  </span>
                  <div className="min-w-0 flex-1">
                    {selectedFile ? (
                      <span className="inline-block rounded-full bg-muted px-2.5 py-0.5 font-mono text-xs font-semibold text-foreground">
                        {selectedFile.name} ({formatBytes(selectedFile.size)})
                      </span>
                    ) : (
                      <>
                        <p className="text-sm text-muted-foreground">
                          PDF 또는 TXT 파일을 끌어오거나{" "}
                          <span className="font-semibold text-primary underline">파일 선택</span>
                        </p>
                        <p className="text-xs text-muted-foreground/60">최대 5MB</p>
                      </>
                    )}
                  </div>
                </div>
              </div>

              <Button
                className="w-full gap-2"
                disabled={!selectedFile || uploadMutation.isPending}
                onClick={() => void onUpload()}
              >
                {uploadMutation.isPending ? (
                  <>
                    <span className="material-symbols-outlined animate-spin text-sm">progress_activity</span>
                    업로드 중...
                  </>
                ) : (
                  <>
                    <span className="material-symbols-outlined text-sm">upload</span>
                    업로드
                  </>
                )}
              </Button>

              {uploadMutation.error && (
                <p className="text-sm font-semibold text-destructive">
                  업로드 오류:{" "}
                  {uploadMutation.error instanceof Error
                    ? uploadMutation.error.message
                    : String(uploadMutation.error)}
                </p>
              )}
            </div>
          </div>

          <div className="rounded-xl border border-border bg-card p-5">
            <div className="mb-4">
              <p className="text-sm font-bold text-foreground">관심 직무</p>
              <p className="mt-1 text-xs text-muted-foreground">
                AI 코치가 이 직무들을 기준으로 이력서와 학습 기록을 분석합니다.
              </p>
            </div>
            <TargetRoleSelector
              value={targetRoles}
              onChange={setTargetRoles}
              disabled={isSavingRoles}
            />
            <Button
              className="mt-4 w-full gap-2"
              onClick={() => void onSaveTargetRoles()}
              disabled={isSavingRoles}
            >
              {isSavingRoles ? (
                <>
                  <span className="material-symbols-outlined animate-spin text-sm">progress_activity</span>
                  저장 중...
                </>
              ) : (
                <>
                  <span className="material-symbols-outlined text-sm">save</span>
                  관심 직무 저장
                </>
              )}
            </Button>
          </div>
        </div>

        {/* ── RIGHT: Mail Schedule + Danger Zone ── */}
        <div className="flex flex-col gap-4">
          <section className="rounded-xl border border-primary/20 bg-primary/5 p-4">
            <div className="flex items-start gap-3">
              <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <span className="material-symbols-outlined text-[20px]">mark_email_unread</span>
              </div>
              <div>
                <p className="text-sm font-bold text-foreground">매일 아침 면접 질문 받기</p>
                <p className="mt-1 text-xs leading-5 text-muted-foreground">
                  추출된 이력서와 관심 기술을 기준으로 정해둔 시간에 연습 질문을 메일로 받아볼 수 있습니다.
                </p>
              </div>
            </div>
          </section>

          <InterviewMailScheduleCard />

          {/* Danger Zone */}
          <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-4">
            <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-destructive/70">
              Danger Zone
            </p>
            <p className="mb-3 text-sm text-destructive/80">
              계정을 삭제하면 모든 데이터가 영구 삭제됩니다.
            </p>
            <Button
              variant="destructive"
              size="sm"
              onClick={onDeleteAccount}
              disabled={deleteAccountMutation.isPending}
              className="w-full"
            >
              {deleteAccountMutation.isPending ? "처리 중..." : "탈퇴하기"}
            </Button>
          </div>
        </div>
      </div>
    </div>
    </>
  );
}
