export type CultureFitFeedback = {
  strengths: string[];
  improvements: string[];
  suggestedAnswer: string | null;
  followups: string[];
  alignmentNote: string;
};

export type CultureFitQuestion = {
  id: number;
  orderIndex: number;
  badge: string;
  likelihood: number;
  questionText: string;
  intention: string | null;
  keywords: string | null;
  modelAnswer: string | null;
  answerText: string | null;
  feedbackStatus: "PENDING" | "COMPLETED" | "FAILED";
  feedback: CultureFitFeedback | null;
};

export type CultureFitSession = {
  id: number;
  companyName: string | null;
  status: "CREATED" | "IN_PROGRESS" | "COMPLETED";
  positionType: string | null;
  questionCount: number;
  createdAt: string | null;
  completedAt: string | null;
  questions: CultureFitQuestion[];
};
