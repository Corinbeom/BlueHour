import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AppBottomNav } from "../AppBottomNav";

const pathnameMock = vi.hoisted(() => vi.fn());

vi.mock("next/navigation", () => ({
  usePathname: () => pathnameMock(),
}));

afterEach(() => {
  cleanup();
});

describe("AppBottomNav", () => {
  beforeEach(() => {
    pathnameMock.mockReturnValue("/dashboard");
  });

  it("주요 탭을 렌더링한다", () => {
    render(<AppBottomNav />);

    expect(screen.getByRole("link", { name: "오늘" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /AI 코치/ })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /준비 도구/ })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /지원 현황/ })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /AI 비서/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /CS 문제풀이/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /프로필/ })).not.toBeInTheDocument();
    expect(screen.getAllByRole("link")).toHaveLength(4);
  });

  it("현재 경로에 해당하는 탭을 활성화한다", () => {
    pathnameMock.mockReturnValue("/resume-analyzer/practice");

    render(<AppBottomNav />);

    const activeTab = screen.getByRole("link", {
      name: /준비 도구/,
      current: "page",
    });

    expect(activeTab).toHaveAttribute("href", "/resume-analyzer");
    expect(activeTab).toHaveClass("text-primary");
    expect(screen.getByRole("link", { name: "오늘" })).not.toHaveAttribute(
      "aria-current"
    );
  });
});
