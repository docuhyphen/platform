/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import * as authoring from "../../../../services/informationRequestAuthoringService.ts";
import RecipientPreviewContent from "./RecipientPreviewContent.tsx";

vi.mock("../../../../services/informationRequestAuthoringService.ts", () => ({
    getInformationRequestRecipientPreview: vi.fn(),
}));

describe("RecipientPreviewContent", () => {
    beforeAll(() => vi.stubGlobal("ResizeObserver", class {
        observe() {
        }

        unobserve() {
        }

        disconnect() {
        }
    }));
    afterEach(() => {
        cleanup();
        vi.clearAllMocks();
    });

    it("retries a failed preview without changing the selected party", async () => {
        vi.mocked(authoring.getInformationRequestRecipientPreview).mockRejectedValue({errorMessage: "Preview temporarily unavailable"});
        render(<RecipientPreviewContent requestId={"request-a"}
                                        partyId={"party-a"}/>);
        expect(await screen.findByRole("alert")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));
        await waitFor(() => expect(authoring.getInformationRequestRecipientPreview).toHaveBeenCalledTimes(2));
        expect(authoring.getInformationRequestRecipientPreview).toHaveBeenLastCalledWith("request-a", "party-a");
    });
});
