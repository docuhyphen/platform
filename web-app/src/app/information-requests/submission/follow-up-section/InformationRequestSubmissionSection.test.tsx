/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as transport from "../../../../services/informationRequestSubmissionService.ts";
import {
    InformationRequestExecutionStandingDto,
    InformationRequestExecutionStandingKind,
    InformationRequestOwnerType,
    InformationRequestResponseWorkspaceDto,
    InformationRequestState,
    InformationRequestTemplateStatus,
} from "../../../models/models.tsx";
import InformationRequestSubmissionSection from "./InformationRequestSubmissionSection.tsx";

vi.mock("../../../../services/informationRequestSubmissionService.ts", () => ({
    getInformationRequestSubmissionPreview: vi.fn(),
    getInformationRequestAmendments: vi.fn(),
    getInformationRequestCarryForwards: vi.fn(),
    createInformationRequestSupplement: vi.fn(),
}));
vi.mock("../../../../services/informationRequestReviewService.ts", () => ({
    getInformationRequestReviewResults: vi.fn().mockResolvedValue([]),
    appealInformationRequestReview: vi.fn(),
}));

const workspace = (
    state: InformationRequestState,
    standing: InformationRequestExecutionStandingDto = active,
): InformationRequestResponseWorkspaceDto => ({
    request: {
        id: "request-a",
        exchangeId: "exchange-a",
        templateVersionId: "version-a",
        ownerType: InformationRequestOwnerType.ORGANIZATION,
        state,
        gatesExchangeClosure: true,
        aggregateRevision: 4,
        createdAt: "2026-09-25T08:00:00Z",
        updatedAt: "2026-09-25T08:00:00Z",
        requestETag: "\"request-a:4\"",
        conditionEvaluations: [],
    },
    title: "Periodic records request",
    templateVersion: {
        id: "version-a",
        templateDefinitionId: "definition-a",
        versionNumber: 1,
        status: InformationRequestTemplateStatus.PUBLISHED,
        sections: [],
        groups: [],
        conditionRules: [],
        requiredCapabilities: [],
        createdAt: "2026-09-25T08:00:00Z",
    },
    responseETag: "\"responses:1\"",
    occurrences: [],
    responses: [],
    supportingEvidenceLinks: [],
    evidenceUploadAvailable: true,
    evidenceMalwareScanning: false,
    executionStanding: standing,
});

const active: InformationRequestExecutionStandingDto = {kind: InformationRequestExecutionStandingKind.ACTIVE};

describe("InformationRequestSubmissionSection", () =>
{
    beforeAll(() =>
    {
        vi.stubGlobal("ResizeObserver", class
        {
            observe() {}
            unobserve() {}
            disconnect() {}
        });
    });

    beforeEach(() =>
    {
        vi.clearAllMocks();
        vi.mocked(transport.getInformationRequestSubmissionPreview).mockRejectedValue("not loaded");
        vi.mocked(transport.getInformationRequestAmendments).mockResolvedValue([]);
        vi.mocked(transport.getInformationRequestCarryForwards).mockResolvedValue([]);
    });
    afterEach(cleanup);

    it("lets a signed-in caller request a supplement of an active request under the request ETag", async () =>
    {
        vi.mocked(transport.createInformationRequestSupplement).mockResolvedValue({outcome: "SAVED", responseETag: "", data: {} as never});

        render(<InformationRequestSubmissionSection workspace={workspace(InformationRequestState.CLOSED)}
                                                    requirements={[]}
                                                    onChanged={vi.fn()}/>);
        fireEvent.click(screen.getByRole("button", {name: "Request more information"}));
        fireEvent.change(screen.getByRole("textbox"), {target: {value: " additional records "}});
        fireEvent.click(screen.getByRole("button", {name: "Create supplement"}));

        await waitFor(() => expect(transport.createInformationRequestSupplement).toHaveBeenCalledWith(
            "request-a",
            "additional records",
            expect.objectContaining({expectedETag: "\"request-a:4\""}),
        ));
        expect(await screen.findByText(/A supplemental request was created as a draft/)).toBeTruthy();
    });

    it("offers no supplement on an access link, before issuance, or once the owner can start no new work", () =>
    {
        render(<InformationRequestSubmissionSection workspace={workspace(InformationRequestState.CLOSED)}
                                                    requirements={[]}
                                                    accessLinkToken={"bootstrap"}
                                                    onChanged={vi.fn()}/>);
        expect(screen.queryByRole("button", {name: "Request more information"})).toBeNull();
        cleanup();

        render(<InformationRequestSubmissionSection workspace={workspace(InformationRequestState.DRAFT)}
                                                    requirements={[]}
                                                    onChanged={vi.fn()}/>);
        expect(screen.queryByRole("button", {name: "Request more information"})).toBeNull();
        cleanup();

        render(<InformationRequestSubmissionSection workspace={workspace(
                                                        InformationRequestState.CLOSED,
                                                        {kind: InformationRequestExecutionStandingKind.CONTINUING_AFTER_LAPSE},
                                                    )}
                                                    requirements={[]}
                                                    onChanged={vi.fn()}/>);
        expect(screen.queryByRole("button", {name: "Request more information"})).toBeNull();
    });
});
