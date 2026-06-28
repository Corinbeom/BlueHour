"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  completeCultureFitSession,
  createCultureFitSession,
  generateCultureFitFeedback,
  getCultureFitSession,
  listCultureFitSessions,
  scrapeCultureFitUrl,
  submitCultureFitAnswer,
} from "../api/cultureFitApi";

export function useCultureFitSessions() {
  return useQuery({
    queryKey: ["cultureFitSessions"],
    queryFn: listCultureFitSessions,
  });
}

export function useCultureFitSession(sessionId: number | null) {
  return useQuery({
    queryKey: ["cultureFitSession", sessionId],
    queryFn: () => getCultureFitSession(sessionId as number),
    enabled: sessionId !== null,
  });
}

export function useScrapeCultureFitUrl() {
  return useMutation({
    mutationFn: scrapeCultureFitUrl,
  });
}

export function useCreateCultureFitSession() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createCultureFitSession,
    onSuccess: (session) => {
      queryClient.invalidateQueries({ queryKey: ["cultureFitSessions"] });
      queryClient.setQueryData(["cultureFitSession", session.id], session);
    },
  });
}

export function useSubmitCultureFitAnswer(sessionId: number | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: submitCultureFitAnswer,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["cultureFitSession", sessionId] });
      queryClient.invalidateQueries({ queryKey: ["cultureFitSessions"] });
    },
  });
}

export function useGenerateCultureFitFeedback(sessionId: number | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: generateCultureFitFeedback,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["cultureFitSession", sessionId] });
      queryClient.invalidateQueries({ queryKey: ["cultureFitSessions"] });
    },
  });
}

export function useCompleteCultureFitSession() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: completeCultureFitSession,
    onSuccess: (session) => {
      queryClient.invalidateQueries({ queryKey: ["cultureFitSessions"] });
      queryClient.setQueryData(["cultureFitSession", session.id], session);
    },
  });
}
