/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import RespondentLoadError from "./RespondentLoadError.tsx";

describe("RespondentLoadError", () => {
    beforeAll(() => {
        vi.stubGlobal("ResizeObserver", class {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
    });

    afterEach(cleanup);

    it("announces why the request could not be opened and offers to try again", () => {
        const onRetry = vi.fn();
        render(<RespondentLoadError message={"Invalid verification code"}
                                    onRetry={onRetry}/>);

        expect(screen.getByRole("alert").textContent).toContain("Invalid verification code");
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));
        expect(onRetry).toHaveBeenCalledTimes(1);
    });
});
