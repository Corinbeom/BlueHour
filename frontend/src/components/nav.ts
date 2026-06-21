export type NavItem = {
  href: string;
  label: string;
  shortLabel?: string;
  description?: string;
  icon: string;
};

export const primaryNav: NavItem[] = [
  { href: "/dashboard", label: "오늘", description: "오늘 할 일과 핵심 현황", icon: "dashboard" },
  { href: "/coach", label: "AI 코치", description: "준비도 점수와 심층 분석", icon: "psychology" },
  { href: "/resume-analyzer", label: "준비 도구", icon: "record_voice_over" },
  { href: "/application-tracker", label: "지원 현황", icon: "work_history" },
];
