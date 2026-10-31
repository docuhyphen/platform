// @vitest-environment jsdom
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, beforeAll, expect, it, vi} from "vitest";
import AuthorWorkspaceMessages from "./AuthorWorkspaceMessages.tsx";

afterEach(cleanup);
beforeAll(() => vi.stubGlobal("ResizeObserver", class {
    observe() {
    }

    unobserve() {
    }

    disconnect() {
    }
}));

it("offers a retry beside a failed workspace read", () => {
    const retry = vi.fn();
    render(<AuthorWorkspaceMessages error={"The request could not be loaded."}
                                    notice={null}
                                    onRetry={retry}/>);

    fireEvent.click(screen.getByRole("button", {name: "Try again"}));

    expect(retry).toHaveBeenCalledOnce();
});
