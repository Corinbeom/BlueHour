"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { primaryNav } from "./nav";
import { useAuth } from "@/features/auth/hooks/useAuth";
import { cn } from "@/lib/utils";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

function isActivePath(pathname: string, href: string) {
  return pathname === href || pathname.startsWith(href + "/");
}

export function AppSidebar() {
  const pathname = usePathname();
  const { user, logout } = useAuth();

  const displayName = user?.displayName ?? user?.email ?? "사용자";
  const initial = displayName.charAt(0).toUpperCase();

  return (
    <aside className="hidden w-[220px] flex-shrink-0 flex-col border-r border-border bg-background dark:bg-surface md:flex">
      {/* Logo — Blade style text mark */}
      <div className="px-5 pb-[18px] pt-[22px]">
        <div className="font-sans text-lg font-bold tracking-[-0.02em] text-foreground">
          Bluehour
        </div>
        <div className="mt-1 font-mono text-[9px] uppercase tracking-[0.1em] text-muted-foreground">
          Before Your Sunrise
        </div>
      </div>

      <div className="mx-5 h-px bg-border" />

      {/* Navigation */}
      <nav className="flex-1 px-2.5 py-3">
        {primaryNav.map((item) => {
          const active = isActivePath(pathname, item.href);
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "mb-0.5 flex w-full items-center gap-2.5 border-l-2 px-2.5 py-[9px] text-[13px] transition-colors duration-150",
                "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-primary/40",
                active
                  ? "border-primary bg-accent/10 font-medium text-foreground"
                  : "border-transparent text-muted-foreground hover:bg-accent/50 hover:text-foreground"
              )}
            >
              <span
                className={cn(
                  "material-symbols-outlined text-[17px]",
                  active ? "text-foreground" : "text-muted-foreground"
                )}
                style={{
                  fontVariationSettings: active
                    ? '"FILL" 1, "wght" 400, "GRAD" 0, "opsz" 24'
                    : '"FILL" 0, "wght" 400, "GRAD" 0, "opsz" 24',
                }}
              >
                {item.icon}
              </span>
              <span className="min-w-0">
                <span className="block truncate">{item.label}</span>
                {item.description && (
                  <span className="mt-0.5 block truncate text-[10px] font-normal text-muted-foreground/80">
                    {item.description}
                  </span>
                )}
              </span>
            </Link>
          );
        })}
      </nav>

      <div className="mx-5 h-px bg-border" />

      {/* User profile */}
      <div className="p-3.5">
        <DropdownMenu>
          <DropdownMenuTrigger className="flex w-full items-center gap-2.5 rounded-lg p-2 transition-colors hover:bg-accent/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/40">
            <Avatar className="size-[26px] flex-shrink-0 border border-border">
              <AvatarImage src={user?.photoUrl ?? undefined} alt={displayName} />
              <AvatarFallback className="bg-primary/10 text-[11px] font-bold text-primary">
                {initial}
              </AvatarFallback>
            </Avatar>
            <div className="min-w-0 flex-1 text-left">
              <p className="truncate text-[12px] font-semibold text-foreground">
                {displayName}
              </p>
              <p className="truncate text-[10px] text-muted-foreground">
                {user?.email}
              </p>
            </div>
            <span className="material-symbols-outlined text-base text-muted-foreground">
              unfold_more
            </span>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-56">
            <DropdownMenuItem
              onClick={() => (window.location.href = "/profile")}
              className="flex items-center gap-2"
            >
              <span className="material-symbols-outlined text-lg">person</span>
              프로필 관리
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onClick={logout}
              className="text-destructive focus:text-destructive"
            >
              <span className="material-symbols-outlined text-lg">logout</span>
              로그아웃
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </aside>
  );
}
