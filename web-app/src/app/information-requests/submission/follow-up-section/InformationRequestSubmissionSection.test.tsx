/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as transport from "../../../../services/informationRequestSubmissionService.ts";
import {usePlanFeature} from "../../../../hooks/subscription/usePlanFeature.ts";
import {
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
vi.mock("../../../../hooks/subscription/usePlanFeature.ts", () => ({usePlanFeature: vi.fn()}));

const workspace = (state: InformationRequestState): InformationRequestResponseWorkspaceDto => ({
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
});

const availability = (isAvailable: boolean) => ({
    isKnown: true,
    isIncluded: isAvailable,
    isEnforced: true,
    isDiscoverable: isAvailable,
    isAvailable,
    upgradePlanCode: null,
});

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

    it("lets a signed-in caller with the feature request a supplement under the request ETag", async () =>
    {
        vi.mocked(usePlanFeature).mockReturnValue(availability(true));
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

    it("offers no supplement on an access link, without the feature, or before issuance", () =>
    {
        vi.mocked(usePlanFeature).mockReturnValue(availability(true));
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

        vi.mocked(usePlanFeature).mockReturnValue(availability(false));
        render(<InformationRequestSubmissionSection workspace={workspace(InformationRequestState.CLOSED)}
                                                    requirements={[]}
                                                    onChanged={vi.fn()}/>);
        expect(screen.queryByRole("button", {name: "Request more information"})).toBeNull();
    });
});
