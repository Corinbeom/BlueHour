"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { primaryNav } from "./nav";
import { cn } from "@/lib/utils";

function isActivePath(pathname: string, href: string) {
  return pathname === href || pathname.startsWith(href + "/");
}

export function AppBottomNav() {
  const pathname = usePathname();

  return (
    <nav className="fixed inset-x-0 bottom-0 z-40 border-t border-border bg-card pb-[env(safe-area-inset-bottom)] md:hidden">
      <div
        className="grid h-16"
        style={{ gridTemplateColumns: `repeat(${primaryNav.length}, minmax(0, 1fr))` }}
      >
        {primaryNav.map((item) => {
          const active = isActivePath(pathname, item.href);
          const displayLabel = item.shortLabel ?? item.label;

          return (
            <Link
              key={item.href}
              href={item.href}
              aria-label={displayLabel}
              aria-current={active ? "page" : undefined}
              className={cn(
                "flex min-h-11 flex-col items-center justify-center gap-0.5 px-1 text-center text-[11px] font-medium transition-colors",
                "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-primary/40",
                active
                  ? "text-primary"
                  : "text-muted-foreground hover:bg-accent/50 hover:text-foreground"
              )}
            >
              <span
                aria-hidden="true"
                className="material-symbols-outlined text-[22px] leading-none"
                style={{
                  fontVariationSettings: active
                    ? '"FILL" 1, "wght" 400, "GRAD" 0, "opsz" 24'
                    : '"FILL" 0, "wght" 400, "GRAD" 0, "opsz" 24',
                }}
              >
                {item.icon}
              </span>
              <span className="max-w-full truncate">{displayLabel}</span>
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
