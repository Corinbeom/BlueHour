export type CoachInferredFrom = "TARGET_ROLES" | "JD_ANALYSIS" | "DEFAULT";

export type CoachSummary = {
  targetRoles: string[];
  inferredFrom: CoachInferredFrom;
  roleCategory: "DEVELOPER" | "DESIGN" | "PRODUCT" | "MARKETING" | "DATA" | "CUSTOM";
  technicalTrack: boolean;
  recruitment: {
    totalApplications: number;
    statusBreakdown: Record<string, number>;
    recentJdTitles: string[];
  };
  resume: {
    uploadedCount: number;
    lastAnalyzedAt: string | null;
  };
  interview: {
    totalSessions: number;
    completedSessions: number;
    averageTurns: number;
  };
  quiz: {
    totalAttempts: number;
    topicAccuracy: Record<string, number>;
    topicAttempts: Record<string, number>;
  };
};

export type CoachPlanItem = {
  d: number;
  do: string;
};

export type CoachAnalysis = {
  score: number;
  primary: string;
  strengths: string[];
  gaps: string[];
  plan: CoachPlanItem[];
  today: string;
  needsTargetRoles: boolean;
};
