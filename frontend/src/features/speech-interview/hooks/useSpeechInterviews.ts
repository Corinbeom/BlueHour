"use client";

import { useQuery } from "@tanstack/react-query";
import { listSpeechInterviews, getSpeechInterview } from "../api/speechInterviewApi";
import type { SpeechInterviewSession } from "../api/types";

export function useSpeechInterviews() {
  return useQuery<SpeechInterviewSession[]>({
    queryKey: ["speechInterviews"],
    queryFn: listSpeechInterviews,
  });
}

export function useSpeechInterview(sessionId: number | null, pollingEnabled = false) {
  return useQuery<SpeechInterviewSession>({
    queryKey: ["speechInterview", sessionId],
    queryFn: () => getSpeechInterview(sessionId!),
    enabled: sessionId !== null,
    refetchInterval: (query) => {
      if (!pollingEnabled) return false;
      const data = query.state.data;
      const hasPending = (data?.questions ?? []).some((q) => q.answer?.feedbackStatus === "PENDING");
      return hasPending ? 10_000 : false;
    },
    refetchIntervalInBackground: false,
  });
}
