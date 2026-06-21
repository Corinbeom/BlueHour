export type RoleCategory = "DEVELOPER" | "DESIGN" | "PRODUCT" | "MARKETING" | "DATA" | "CUSTOM";

export function normalizeRole(role: string) {
  return role.trim().replace(/\s+/g, "").toUpperCase();
}

function includesAny(value: string, keywords: string[]) {
  return keywords.some((keyword) => value.includes(normalizeRole(keyword)));
}

export function getRoleCategory(roles?: string[]): RoleCategory {
  if (!roles?.length) return "CUSTOM";

  for (const role of roles) {
    const normalized = normalizeRole(role);
    if (
      includesAny(normalized, [
        "프론트엔드",
        "백엔드",
        "풀스택",
        "iOS",
        "Android",
        "안드로이드",
        "데이터 엔지니어",
        "DevOps",
        "개발자",
        "엔지니어",
      ])
    ) {
      return "DEVELOPER";
    }
    if (includesAny(normalized, ["데이터 분석", "데이터 사이언티스트", "데이터 과학", "분석가"])) {
      return "DATA";
    }
    if (includesAny(normalized, ["디자이너", "디자인", "UX", "UI", "그래픽", "브랜드 디자"])) {
      return "DESIGN";
    }
    if (includesAny(normalized, ["프로덕트", "PM", "PO", "기획", "서비스 기획", "콘텐츠 기획"])) {
      return "PRODUCT";
    }
    if (includesAny(normalized, ["마케터", "마케팅", "퍼포먼스", "그로스"])) {
      return "MARKETING";
    }
  }

  return "CUSTOM";
}

export function isTechnicalTrack(roles?: string[]) {
  return getRoleCategory(roles) === "DEVELOPER";
}
