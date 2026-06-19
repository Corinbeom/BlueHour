// SpeechInterview 도메인 독립 타입 — resume 도메인 타입 의존 없음

export type SpeechInterviewQuestion = {
  id: number;
  orderIndex: number;
  badge: string;
  questionText: string;
  answer?: SpeechInterviewAnswer;
};

export type SpeechInterviewAnswer = {
  answerText: string;
  feedbackStatus: "PENDING" | "COMPLETED" | "FAILED";
  feedback?: SpeechFeedback;
};

export type SpeechFeedback = {
  strengths: string[];
  improvements: string[];
  suggestedAnswer: string;
  followups: string[];
  deliveryStrengths: string[];
  deliveryImprovements: string[];
};

export type SpeechInterviewSession = {
  id: number;
  title: string;
  positionType: string | null;
  status: "CREATED" | "IN_PROGRESS" | "COMPLETED";
  createdAt: string;
  completedAt?: string;
  questions: SpeechInterviewQuestion[];
};

export type ChatRequest = {
  userMessage: string;
};

export type ChatResponse = {
  aiMessage: string;
  turnIndex: number;
  isComplete: boolean;
  questionId: number | null;
  badge: string | null;
};
