"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/features/auth/hooks/useAuth";
import { updateMyTargetRoles } from "@/features/member/api/memberApi";
import { TargetRoleSelector } from "@/features/member/components/TargetRoleSelector";

export default function OnboardingPage() {
  const router = useRouter();
  const { user, isLoading, refresh } = useAuth();
  const [targetRoles, setTargetRoles] = useState<string[]>([]);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    if (isLoading) return;
    if (!user) {
      router.replace("/login");
      return;
    }
    if (user.onboardingCompleted) {
      router.replace("/dashboard");
    }
  }, [isLoading, user, router]);

  async function saveRoles(roles: string[]) {
    setIsSaving(true);
    try {
      await updateMyTargetRoles(roles);
      await refresh();
      router.replace("/dashboard");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "관심 직무 저장에 실패했습니다.");
    } finally {
      setIsSaving(false);
    }
  }

  if (isLoading || !user || user.onboardingCompleted) {
    return null;
  }

  return (
    <main className="min-h-screen bg-background px-5 py-10 text-foreground">
      <div className="mx-auto flex min-h-[calc(100vh-5rem)] w-full max-w-3xl flex-col justify-center gap-8">
        <div>
          <p className="text-sm font-semibold text-muted-foreground">Bluehour AI Coach</p>
          <h1 className="mt-2 text-3xl font-bold tracking-normal text-foreground">
            준비 중인 직무를 알려주세요
          </h1>
          <p className="mt-3 max-w-xl text-sm leading-6 text-muted-foreground">
            선택한 직무를 기준으로 이력서, 지원 현황, 면접 연습 기록을 묶어 분석합니다.
            개발 직무는 CS 학습 기록도 함께 반영합니다.
            최대 3개까지 선택할 수 있습니다.
          </p>
        </div>

        <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
          <TargetRoleSelector value={targetRoles} onChange={setTargetRoles} disabled={isSaving} />
        </section>

        <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
          <Button
            disabled={targetRoles.length === 0 || isSaving}
            onClick={() => void saveRoles(targetRoles)}
            className="h-11 px-6"
          >
            {isSaving ? "저장 중..." : "시작하기"}
          </Button>
          <button
            type="button"
            disabled={isSaving}
            onClick={() => void saveRoles([])}
            className="h-11 px-2 text-sm font-semibold text-muted-foreground transition-colors hover:text-foreground disabled:opacity-50"
          >
            나중에 설정
          </button>
        </div>
      </div>
    </main>
  );
}
