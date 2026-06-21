export type NavItem = {
  href: string;
  label: string;
  shortLabel?: string; // bottom nav 5-col용 축약 라벨
  description?: string;
  icon: string; // material symbol name
};

export const primaryNav: NavItem[] = [
  { href: "/dashboard", label: "오늘 현황", shortLabel: "현황", description: "지원, 면접, 퀴즈 진행 상태", icon: "dashboard" },
  { href: "/coach", label: "AI 코치", description: "준비도 점수와 심층 분석", icon: "psychology" },
  { href: "/assistant", label: "AI 비서", icon: "support_agent" },
  { href: "/resume-analyzer", label: "AI 면접 준비", shortLabel: "면접 준비", icon: "record_voice_over" },
  { href: "/study-quiz", label: "CS 문제풀이", icon: "quiz" },
  { href: "/application-tracker", label: "지원 현황", icon: "work_history" },
  { href: "/profile", label: "프로필", icon: "person" },
];
