/** @vitest-environment jsdom */
import {cleanup, render, screen} from "@testing-library/react";
import {MemoryRouter} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import apiClient from "../../../services/apiClient.ts";
import InformationRequestRespondentWorkspace from "./InformationRequestRespondentWorkspace.tsx";

vi.mock("../../../services/apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn()}}));

describe("InformationRequestRespondentWorkspace opened from an incomplete link", () => {
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

    beforeEach(() => {
        vi.resetAllMocks();
        window.sessionStorage.clear();
    });

    afterEach(cleanup);

    it("asks for the original link instead of offering a verification that cannot be sent", async () => {
        render(
            <MemoryRouter initialEntries={["/nir?s=request-a"]}>
                <InformationRequestRespondentWorkspace accessMode={"no-auth"}/>
            </MemoryRouter>,
        );

        expect(await screen.findByText("This link is incomplete. Open the link you were sent again, in full.")).toBeTruthy();
        expect(screen.queryByRole("region", {name: "Verify access"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Send verification code"})).toBeNull();
        expect(apiClient.post).not.toHaveBeenCalled();
    });
});
