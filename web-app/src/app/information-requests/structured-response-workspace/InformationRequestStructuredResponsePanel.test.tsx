/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as reuse from "../../../services/informationRequestReuseService.ts";
import {
    InformationRequestDto,
    InformationRequestExecutionStandingKind,
    InformationRequestOwnerType,
    InformationRequestState,
} from "../../models/models.tsx";
import InformationRequestStructuredResponsePanel from "./InformationRequestStructuredResponsePanel.tsx";

const usePlanFeature = vi.fn();

vi.mock("../../../services/informationRequestReuseService.ts", () => ({
    getInformationRequestAcceptedFactOffers: vi.fn(),
    recertifyInformationRequestAcceptedFact: vi.fn(),
}));

vi.mock("../../../hooks/subscription/usePlanFeature.ts", () => ({
    usePlanFeature: (...args: unknown[]) => usePlanFeature(...args),
}));

const request: InformationRequestDto = {
    id: "request-1",
    exchangeId: "exchange-1",
    templateVersionId: "template-version-1",
    ownerType: InformationRequestOwnerType.USER,
    state: InformationRequestState.IN_PROGRESS,
    gatesExchangeClosure: true,
    aggregateRevision: 3,
    createdAt: Date.parse("2026-09-08T00:00:00Z"),
    updatedAt: Date.parse("2026-09-08T00:00:00Z"),
    requestETag: "\"request:3\"",
    executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
    conditionEvaluations: [],
};

const renderPanel = (accessLinkToken?: string) => render(
    <InformationRequestStructuredResponsePanel request={request}
                                               respondentKey={"respondent-a"}
                                               responseETag={"\"responses:1\""}
                                               groups={[]}
                                               creatableGroupKeys={[]}
                                               occurrences={[]}
                                               requirements={[]}
                                               bindings={[]}
                                               responses={[]}
                                               evidenceUploadAvailable={true}
                                               evidenceMalwareScanning={false}
                                               accessLinkToken={accessLinkToken}
                                               onRefresh={vi.fn()}/>
);

describe("InformationRequestStructuredResponsePanel", () =>
{
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
        vi.clearAllMocks();
        vi.mocked(reuse.getInformationRequestAcceptedFactOffers).mockResolvedValue([]);
    });

    afterEach(cleanup);

    it("shows the authenticated workspace after server-side owner-funded access succeeds", () =>
    {
        usePlanFeature.mockReturnValue({isAvailable: false});

        renderPanel();

        expect(document.getElementById("information-request-response-workspace")).toBeTruthy();
    });

    it("shows the same workspace to an entitled authenticated caller", () =>
    {
        usePlanFeature.mockReturnValue({isAvailable: true});

        renderPanel();

        expect(document.getElementById("information-request-response-workspace")).toBeTruthy();
    });

    it("shows the workspace to a no-auth respondent whose access is gated by the server", () =>
    {
        usePlanFeature.mockReturnValue({isAvailable: false});

        renderPanel("access-token");

        expect(document.getElementById("information-request-response-workspace")).toBeTruthy();
    });

    it("states a refused read of answers offered for reuse with Try again and clears it once a retry loads", async () => {
        usePlanFeature.mockReturnValue({isAvailable: true});
        vi.mocked(reuse.getInformationRequestAcceptedFactOffers)
            .mockRejectedValueOnce({errorMessage: "Earlier accepted answers could not be read."})
            .mockResolvedValueOnce([]);

        renderPanel();

        await waitFor(() => expect(document.getElementById("information-request-reusable-answers-load-error")?.textContent)
            .toContain("Earlier accepted answers could not be read."));
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));
        await waitFor(() => expect(document.getElementById("information-request-reusable-answers-load-error")).toBeNull());
        expect(reuse.getInformationRequestAcceptedFactOffers).toHaveBeenCalledTimes(2);
    });
});

