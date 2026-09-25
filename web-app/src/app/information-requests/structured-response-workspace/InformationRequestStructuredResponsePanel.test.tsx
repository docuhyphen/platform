/** @vitest-environment jsdom */
import {cleanup, render} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {
    InformationRequestDto,
    InformationRequestOwnerType,
    InformationRequestState,
} from "../../models/models.tsx";
import InformationRequestStructuredResponsePanel from "./InformationRequestStructuredResponsePanel.tsx";

const usePlanFeature = vi.fn();

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
    createdAt: "2026-09-08T00:00:00Z",
    updatedAt: "2026-09-08T00:00:00Z",
    requestETag: "\"request:3\"",
    conditionEvaluations: [],
};

const renderPanel = (accessLinkToken?: string) => render(
    <InformationRequestStructuredResponsePanel request={request}
                                               responseETag={"\"responses:1\""}
                                               groups={[]}
                                               conditionRules={[]}
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
    beforeEach(() => vi.clearAllMocks());

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
});

