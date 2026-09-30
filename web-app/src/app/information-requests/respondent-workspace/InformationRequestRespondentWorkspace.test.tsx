/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import {
    InformationRequestContributorRole,
    InformationRequestExecutionStandingKind,
    InformationRequestOwnerType,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseMode,
    InformationRequestResponseWorkspaceDto,
    InformationRequestReviewPolicy,
    InformationRequestState,
    InformationRequestTemplateStatus,
} from "../../models/models.tsx";
import InformationRequestRespondentWorkspace from "./InformationRequestRespondentWorkspace.tsx";
import {unnamedControls} from "../shared/testing/unnamedControls.ts";

const workspace: InformationRequestResponseWorkspaceDto = {
    request: {
        id: "request-a",
        exchangeId: "exchange-a",
        templateVersionId: "version-a",
        ownerType: InformationRequestOwnerType.ORGANIZATION,
        state: InformationRequestState.IN_PROGRESS,
        gatesExchangeClosure: true,
        aggregateRevision: 3,
        createdAt: "2026-09-27T08:00:00Z",
        updatedAt: "2026-09-27T08:00:00Z",
        requestETag: "\"request-3\"",
        conditionEvaluations: [],
    },
    title: "Periodic records request",
    templateVersion: {
        id: "version-a",
        templateDefinitionId: "definition-a",
        versionNumber: 1,
        status: InformationRequestTemplateStatus.PUBLISHED,
        sections: [{
            id: "section-a",
            sectionKey: "records",
            title: "Records",
            requirements: [{
                id: "binding-a",
                templateRequirementId: "requirement-a",
                requirementKey: "record-count",
                requirementType: InformationRequestRequirementType.FIELD,
                prompt: "How many records were kept?",
                responseMode: InformationRequestResponseMode.PROVIDE,
                requiredness: InformationRequestRequiredness.REQUIRED,
                contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
                reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
                permittedDispositions: [InformationRequestResponseDisposition.PROVIDED],
                substituteRequirementKeys: [],
                supportingEvidenceRequirementKeys: [],
            }],
        }],
        groups: [],
        conditionRules: [],
        requiredCapabilities: [],
        createdAt: "2026-09-20T08:00:00Z",
    },
    responseETag: "\"responses-1\"",
    occurrences: [],
    responses: [{
        informationRequestRequirementId: "runtime-a",
        sourceTemplateRequirementId: "requirement-a",
        sourceTemplateBindingId: "binding-a",
        occurrencePath: "root",
        disposition: InformationRequestResponseDisposition.NOT_ANSWERED,
        fieldValues: [],
        responseRevision: 1,
        updatedAt: "2026-09-27T08:00:00Z",
    }],
    supportingEvidenceLinks: [],
    evidenceUploadAvailable: true,
    evidenceMalwareScanning: false,
    executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
};

vi.mock("./useInformationRequestRespondentWorkspace.ts", () => ({
    useInformationRequestRespondentWorkspace: () => ({
        requestId: "request-a",
        workspace,
        requirements: workspace.templateVersion.sections.flatMap(section => section.requirements),
        accessVerified: true,
        challengeSent: false,
        code: "",
        busy: false,
        error: null,
        setCode: vi.fn(),
        issueChallenge: vi.fn(),
        verifyCode: vi.fn(),
        loadWorkspace: vi.fn(),
    }),
}));
vi.mock("../structured-response-workspace/InformationRequestStructuredResponsePanel.tsx", () => ({
    default: () => <div id={"information-request-response-requirement-root-record-count"}
                        tabIndex={-1}>Response form</div>,
}));
vi.mock("../submission/follow-up-section/InformationRequestSubmissionSection.tsx", () => ({default: () => <div>Submission</div>}));

describe("InformationRequestRespondentWorkspace", () =>
{
    beforeAll(() =>
    {
        vi.stubGlobal("ResizeObserver", class
        {
            observe() {}
            unobserve() {}
            disconnect() {}
        });
        Element.prototype.scrollIntoView = vi.fn();
    });

    afterEach(() =>
    {
        cleanup();
        workspace.executionStanding = {kind: InformationRequestExecutionStandingKind.ACTIVE};
    });

    it("tells a party that changes are paused without stating the owner's reason", () =>
    {
        workspace.executionStanding = {kind: InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED};
        render(<InformationRequestRespondentWorkspace accessMode={"no-auth"}/>);

        expect(screen.getByText(/Changes to this request are paused/)).toBeTruthy();
        expect(screen.queryByText(/suspended/)).toBeNull();
    });

    it("names the request and lists its sections with their progress, each opening its first item", () =>
    {
        render(<InformationRequestRespondentWorkspace accessMode={"authenticated"}/>);

        expect(screen.getByRole("heading", {name: "Periodic records request"})).toBeTruthy();
        const navigation = screen.getByRole("navigation", {name: "Sections"});
        expect(navigation.textContent).toContain("0 of 1 required answered");
        fireEvent.click(screen.getByRole("button", {name: "Records, 0 of 1 required answered"}));

        expect(document.activeElement?.id).toBe("information-request-response-requirement-root-record-count");
    });

    it("names every control and region of the respondent shell", () =>
    {
        render(<InformationRequestRespondentWorkspace accessMode={"authenticated"}/>);

        expect(unnamedControls(document.body)).toEqual([]);
    });

    it("gives a verified no-auth respondent the same workspace as a signed-in one", () =>
    {
        const signedIn = render(<InformationRequestRespondentWorkspace accessMode={"authenticated"}/>);
        const signedInSections = screen.getByRole("navigation", {name: "Sections"}).textContent;
        signedIn.unmount();

        render(<InformationRequestRespondentWorkspace accessMode={"no-auth"}/>);

        expect(screen.getByRole("heading", {name: "Periodic records request"})).toBeTruthy();
        expect(screen.getByRole("navigation", {name: "Sections"}).textContent).toBe(signedInSections);
        expect(screen.getByText("Response form")).toBeTruthy();
    });
});
