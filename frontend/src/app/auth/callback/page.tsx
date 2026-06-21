"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/features/auth/hooks/useAuth";

export default function AuthCallbackPage() {
  const { refresh } = useAuth();
  const router = useRouter();

  useEffect(() => {
    let cancelled = false;

    async function handleCallback() {
      const user = await refresh();
      if (cancelled) return;
      if (user) {
        router.replace(user.onboardingCompleted ? "/dashboard" : "/onboarding");
      } else {
        router.replace("/login");
      }
    }

    handleCallback();
    return () => {
      cancelled = true;
    };
  }, [refresh, router]);

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-background-light dark:bg-background-dark">
      <span className="material-symbols-outlined animate-spin text-5xl text-primary">
        progress_activity
      </span>
      <p className="text-sm font-medium text-slate-500 dark:text-slate-400">
        로그인 처리 중...
      </p>
    </div>
  );
}
