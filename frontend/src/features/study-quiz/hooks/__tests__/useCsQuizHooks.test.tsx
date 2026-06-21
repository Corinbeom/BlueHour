import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { PropsWithChildren } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  createCsQuizSession,
  deleteCsQuizSession,
  getCsQuizSession,
  listCsQuizSessions,
} from "../../api/studyQuizApi";
import { useCsQuizSession } from "../useCsQuizSession";
import { useCsQuizSessions } from "../useCsQuizSessions";
import {
  useCreateCsQuizSession,
  useDeleteCsQuizSession,
} from "../useCsQuizMutations";

vi.mock("../../api/studyQuizApi", () => ({
  createCsQuizSession: vi.fn(),
  deleteCsQuizSession: vi.fn(),
  getCsQuizSession: vi.fn(),
  listCsQuizSessions: vi.fn(),
  submitCsQuizAttempt: vi.fn(),
}));

function createWrapper(queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
})) {
  return {
    queryClient,
    wrapper: ({ children }: PropsWithChildren) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    ),
  };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("CS quiz hooks", () => {
  it("useCsQuizSessions는 세션 목록을 조회한다", async () => {
    vi.mocked(listCsQuizSessions).mockResolvedValueOnce([{ id: 1, title: "OS" }] as never);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useCsQuizSessions(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([{ id: 1, title: "OS" }]);
    expect(listCsQuizSessions).toHaveBeenCalledTimes(1);
  });

  it("useCsQuizSession은 sessionId가 null이면 조회하지 않는다", () => {
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useCsQuizSession(null), { wrapper });

    expect(result.current.fetchStatus).toBe("idle");
    expect(getCsQuizSession).not.toHaveBeenCalled();
  });

  it("useCsQuizSession은 숫자 sessionId로 상세를 조회한다", async () => {
    vi.mocked(getCsQuizSession).mockResolvedValueOnce({ id: 7, title: "DB" } as never);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useCsQuizSession(7), { wrapper });

    await waitFor(() => expect(result.current.data).toEqual({ id: 7, title: "DB" }));
    expect(getCsQuizSession).toHaveBeenCalledWith(7);
  });

  it("useCreateCsQuizSession은 생성 API를 호출한다", async () => {
    vi.mocked(createCsQuizSession).mockResolvedValueOnce({ id: 3 } as never);
    const { wrapper } = createWrapper();
    const { result } = renderHook(() => useCreateCsQuizSession(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({
        difficulty: "LOW",
        topics: ["NETWORK"],
        questionCount: 5,
      });
    });

    expect(vi.mocked(createCsQuizSession).mock.calls[0][0]).toEqual({
      difficulty: "LOW",
      topics: ["NETWORK"],
      questionCount: 5,
    });
  });

  it("useDeleteCsQuizSession은 삭제 후 목록 쿼리를 invalidate한다", async () => {
    vi.mocked(deleteCsQuizSession).mockResolvedValueOnce(undefined as never);
    const { queryClient, wrapper } = createWrapper();
    queryClient.setQueryData(["csQuizSessions"], [{ id: 1 }]);
    const { result } = renderHook(() => useDeleteCsQuizSession(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync(1);
    });

    expect(vi.mocked(deleteCsQuizSession).mock.calls[0][0]).toBe(1);
    expect(queryClient.getQueryState(["csQuizSessions"])?.isInvalidated).toBe(true);
  });

});
