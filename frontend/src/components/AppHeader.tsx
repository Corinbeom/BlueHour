"use client";

import { usePathname } from "next/navigation";
import { useTheme } from "next-themes";

function headerLabel(pathname: string): string {
  if (pathname.startsWith("/coach")) return "AI 코치";
  if (pathname.startsWith("/application-tracker")) return "지원 현황";
  if (pathname.startsWith("/study-quiz")) return "CS 문제풀이";
  if (pathname.startsWith("/resume-analyzer")) return "준비 도구";
  if (pathname.startsWith("/profile")) return "프로필";
  return "오늘";
}

interface AppHeaderProps {
  detailOpen?: boolean;
  onToggleDetail?: () => void;
}

export function AppHeader({ detailOpen, onToggleDetail }: AppHeaderProps) {
  const pathname = usePathname();
  const { resolvedTheme, setTheme } = useTheme();
  const label = headerLabel(pathname);
  const isDark = resolvedTheme === "dark";

  return (
    <header className="flex h-12 flex-shrink-0 items-center justify-between border-b border-border bg-card px-6">
      <span className="text-[13px] font-semibold text-foreground">{label}</span>
      <div className="flex items-center gap-1.5">
        <button
          type="button"
          onClick={() => setTheme(isDark ? "light" : "dark")}
          className="flex size-9 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-accent hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/40"
          aria-label={isDark ? "라이트 모드로 전환" : "다크 모드로 전환"}
          title={isDark ? "라이트 모드" : "다크 모드"}
        >
          <span className="material-symbols-outlined text-[18px]">
            {isDark ? "sunny" : "dark_mode"}
          </span>
        </button>
        {onToggleDetail && (
          <button
            type="button"
            onClick={onToggleDetail}
            className="hidden size-9 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-accent hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/40 xl:flex"
            aria-label={detailOpen ? "패널 숨기기" : "패널 보기"}
            title={detailOpen ? "패널 숨기기" : "패널 보기"}
          >
            <span className="material-symbols-outlined text-[18px]">
              {detailOpen ? "right_panel_close" : "right_panel_open"}
            </span>
          </button>
        )}
      </div>
    </header>
  );
}
