/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as administration from "../../../../services/informationRequestAdministrationService.ts";
import * as outcomes from "../../../../services/informationRequestOutcomeService.ts";
import {
    FieldValueType,
    InformationRequestAcceptedFactConfidence,
    InformationRequestAcceptedFactConflictState,
    InformationRequestAcceptedFactDto,
    InformationRequestAcceptedFactFreshness,
    InformationRequestAcceptedFactVisibility,
    InformationRequestBusinessDecisionDto,
    InformationRequestBusinessDecisionKind,
    InformationRequestCompletenessItemState,
    InformationRequestPrivacyRequestKind,
    InformationRequestPrivacyRequestState,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestState,
    InformationRequestSubmissionItemDto,
    InformationRequestSubmissionPackageDto,
} from "../../../models/models.tsx";
import RequestOutcomesPanel from "./RequestOutcomesPanel.tsx";
import {unnamedControls} from "../../shared/testing/unnamedControls.ts";

vi.mock("../../../../services/informationRequestOutcomeService.ts", () => ({
    getInformationRequestSubmissionPackages: vi.fn(),
    getInformationRequestAcceptedFacts: vi.fn(),
    promoteInformationRequestAcceptedFact: vi.fn(),
    revokeInformationRequestAcceptedFact: vi.fn(),
    getInformationRequestBusinessDecisions: vi.fn(),
    recordInformationRequestBusinessDecision: vi.fn(),
}));
vi.mock("../../../../services/informationRequestAdministrationService.ts", () => ({recordInformationRequestPrivacyRequest: vi.fn()}));

const item = (overrides: Partial<InformationRequestSubmissionItemDto>): InformationRequestSubmissionItemDto => ({
    id: "item-a",
    requirementId: "requirement-a",
    requirementKey: "contact_time",
    requirementType: InformationRequestRequirementType.FIELD,
    occurrencePath: "",
    completenessState: InformationRequestCompletenessItemState.COMPLETE,
    disposition: InformationRequestResponseDisposition.PROVIDED,
    fieldValue: "Mornings",
    fieldValueCleared: false,
    evidence: [],
    ...overrides,
});

const submission = (overrides: Partial<InformationRequestSubmissionPackageDto>): InformationRequestSubmissionPackageDto => ({
    id: "package-a",
    informationRequestId: "request-a",
    packageNumber: 1,
    templateVersionId: "version-a",
    contentHash: "a",
    manifestHash: "b",
    reviewRequired: false,
    completesRequest: true,
    submittedAt: "2026-09-20T10:00:00Z",
    submittedByCaller: false,
    withdrawn: false,
    items: [],
    attestations: [],
    supportingEvidenceLinks: [],
    undisclosedItemCount: 0,
    ...overrides,
});

const fact = (overrides: Partial<InformationRequestAcceptedFactDto>): InformationRequestAcceptedFactDto => ({
    id: "fact-a",
    subjectIdentityRefId: "subject-a",
    purposeKey: "contact-details",
    policyBasisKey: "policy.contact",
    evidenceVersionIds: [],
    fieldDefinitionId: "field-a",
    valueType: FieldValueType.SHORT_TEXT,
    value: "Mornings",
    sourceInformationRequestId: "request-a",
    sourcePackageId: "package-a",
    sourceSubmissionItemId: "item-a",
    sourceRequirementId: "requirement-a",
    visibility: InformationRequestAcceptedFactVisibility.REQUESTING_SIDE,
    confidence: InformationRequestAcceptedFactConfidence.DECLARED,
    validFrom: "2026-09-20T10:00:00Z",
    conflictState: InformationRequestAcceptedFactConflictState.NONE,
    promotedAt: "2026-09-21T10:00:00Z",
    revoked: false,
    freshness: InformationRequestAcceptedFactFreshness.CURRENT,
    ...overrides,
});

const decision = (overrides: Partial<InformationRequestBusinessDecisionDto>): InformationRequestBusinessDecisionDto => ({
    id: "decision-a",
    informationRequestId: "request-a",
    owningProcessKey: "intake",
    outcomeCode: "approved",
    kind: InformationRequestBusinessDecisionKind.ORIGINAL,
    decisionRevision: 1,
    decidedAt: "2026-09-22T10:00:00Z",
    recordedAt: "2026-09-22T10:05:00Z",
    recordedByCaller: true,
    ...overrides,
});

const prompts = {"requirement-a": "Preferred contact time", "requirement-c": "Second contact time"};

const renderPanel = (state = InformationRequestState.CLOSED, subjectId?: string, canCorrect = false) => render(
    <RequestOutcomesPanel requestId={"request-a"}
                          state={state}
                          prompts={prompts}
                          subjectId={subjectId}
                          canCorrect={canCorrect}/>,
);

describe("RequestOutcomesPanel", () =>
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
        vi.mocked(outcomes.getInformationRequestSubmissionPackages).mockResolvedValue([
            submission({
                items: [
                    item({}),
                    item({
                        id: "item-b",
                        requirementId: "requirement-b",
                        requirementKey: "supporting_file",
                        requirementType: InformationRequestRequirementType.DOCUMENT,
                        fieldValue: undefined,
                    }),
                    item({
                        id: "item-d",
                        requirementId: "requirement-d",
                        requirementKey: "skipped_value",
                        disposition: InformationRequestResponseDisposition.NOT_APPLICABLE,
                        fieldValue: undefined,
                    }),
                ],
            }),
            submission({
                id: "package-w",
                packageNumber: 2,
                withdrawn: true,
                items: [item({id: "item-c", requirementId: "requirement-c", fieldValue: "Evenings"})],
            }),
        ]);
        vi.mocked(outcomes.getInformationRequestAcceptedFacts).mockResolvedValue([]);
        vi.mocked(outcomes.getInformationRequestBusinessDecisions).mockResolvedValue([]);
        vi.mocked(outcomes.promoteInformationRequestAcceptedFact).mockResolvedValue(fact({}));
        vi.mocked(outcomes.revokeInformationRequestAcceptedFact).mockResolvedValue(fact({revoked: true}));
        vi.mocked(outcomes.recordInformationRequestBusinessDecision).mockResolvedValue(decision({}));
    });

    afterEach(cleanup);

    it("offers only answered values of current packages and promotes one with its purpose and visibility", async () =>
    {
        renderPanel();

        const promote = await screen.findByRole("button", {name: "Promote Preferred contact time"});
        expect(screen.queryByRole("button", {name: "Promote Second contact time"})).toBeNull();
        expect(screen.queryByRole("button", {name: /supporting file/})).toBeNull();
        expect(screen.queryByRole("button", {name: /skipped value/})).toBeNull();
        expect(screen.getByText("Mornings")).toBeTruthy();

        fireEvent.click(promote);
        const dialog = await screen.findByRole("dialog", {name: "Promote Preferred contact time"});
        const purpose = within(dialog).getByLabelText(/Purpose/);
        fireEvent.change(purpose, {target: {value: "Contact details"}});
        expect(within(dialog).getByText("Use lowercase letters, digits, dots, dashes, or underscores, starting with a letter or digit.")).toBeTruthy();
        expect((within(dialog).getByRole("button", {name: "Promote"}) as HTMLButtonElement).disabled).toBe(true);
        fireEvent.change(purpose, {target: {value: "contact-details"}});
        const policyBasis = within(dialog).getByLabelText(/Reuse policy basis/);
        fireEvent.change(policyBasis, {target: {value: "policy.contact"}});
        fireEvent.change(within(dialog).getByLabelText("Who may see it"), {
            target: {value: InformationRequestAcceptedFactVisibility.RESPONDING_PARTIES},
        });
        fireEvent.click(within(dialog).getByRole("button", {name: "Promote"}));

        await waitFor(() => expect(outcomes.promoteInformationRequestAcceptedFact).toHaveBeenCalledWith(
            "request-a",
            {
                packageId: "package-a",
                submissionItemId: "item-a",
                purposeKey: "contact-details",
                policyBasisKey: "policy.contact",
                visibility: InformationRequestAcceptedFactVisibility.RESPONDING_PARTIES,
            },
            expect.any(String),
        ));
        expect(await screen.findByText("The answer was promoted as an accepted fact.")).toBeTruthy();
        expect(outcomes.getInformationRequestAcceptedFacts).toHaveBeenCalledTimes(2);
    });

    it("pins only conforming evidence that supports the promoted answer in the same package", async () =>
    {
        vi.mocked(outcomes.getInformationRequestSubmissionPackages).mockResolvedValue([
            submission({
                supportingEvidenceLinks: [{supportedRequirementId: "requirement-a", supportingRequirementId: "requirement-b"}],
                items: [
                    item({}),
                    item({
                        id: "item-b",
                        requirementId: "requirement-b",
                        requirementKey: "supporting_file",
                        requirementType: InformationRequestRequirementType.DOCUMENT,
                        fieldValue: undefined,
                        evidence: [
                            {artifactId: "artifact-b", evidenceVersionId: "version-kept", versionNumber: 2, conformance: "CONFORMING"},
                            {artifactId: "artifact-b", evidenceVersionId: "version-refused", versionNumber: 1, conformance: "NONCONFORMING"},
                        ],
                    }),
                ],
            }),
        ]);
        renderPanel();

        fireEvent.click(await screen.findByRole("button", {name: "Promote Preferred contact time"}));
        const dialog = await screen.findByRole("dialog", {name: "Promote Preferred contact time"});
        const supporting = within(dialog).getByRole("group", {name: "Supporting evidence to keep with the fact"});
        expect(within(supporting).getAllByRole("checkbox")).toHaveLength(1);
        fireEvent.click(within(supporting).getByRole("checkbox", {name: "supporting file, version 2"}));
        fireEvent.change(within(dialog).getByLabelText(/Purpose/), {target: {value: "contact-details"}});
        fireEvent.change(within(dialog).getByLabelText(/Reuse policy basis/), {target: {value: "policy.contact"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Promote"}));

        await waitFor(() => expect(outcomes.promoteInformationRequestAcceptedFact).toHaveBeenCalledWith(
            "request-a",
            expect.objectContaining({submissionItemId: "item-a", evidenceVersionIds: ["version-kept"]}),
            expect.any(String),
        ));
    });

    it("names every control and region of the outcomes and their dialogs", async () =>
    {
        renderPanel(InformationRequestState.CLOSED, "subject-a", true);
        fireEvent.click(await screen.findByRole("button", {name: "Record decision"}));
        await screen.findByRole("dialog", {name: "Record a business decision"});

        expect(unnamedControls(document.body)).toEqual([]);
    });

    it("states each promoted fact's standing and revokes one with a reason", async () =>
    {
        vi.mocked(outcomes.getInformationRequestAcceptedFacts).mockResolvedValue([
            fact({
                confidence: InformationRequestAcceptedFactConfidence.REVIEWED,
                conflictState: InformationRequestAcceptedFactConflictState.CONFLICTING,
            }),
            fact({
                id: "fact-b",
                purposeKey: "follow-up",
                freshness: InformationRequestAcceptedFactFreshness.EXPIRED,
                revoked: true,
                revokedAt: "2026-09-23T10:00:00Z",
                revocationReasonCode: "Corrected",
            }),
        ]);
        renderPanel();

        const facts = await screen.findByRole("list", {name: "Promoted facts"});
        const current = within(facts).getAllByRole("listitem")[0];
        expect(within(current).getByText(/Accepted by review/)).toBeTruthy();
        expect(within(current).getByText(/Differs from another current fact about the same subject/)).toBeTruthy();
        const revoked = within(facts).getAllByRole("listitem")[1];
        expect(within(revoked).getByText(/Expired/)).toBeTruthy();
        expect(within(revoked).getByText(/Revoked/)).toBeTruthy();
        expect(within(revoked).queryByRole("button", {name: /Revoke/})).toBeNull();

        fireEvent.click(within(current).getByRole("button", {name: "Revoke Preferred contact time for contact details"}));
        const dialog = await screen.findByRole("dialog", {name: "Revoke this accepted fact"});
        fireEvent.change(within(dialog).getByLabelText(/Reason/), {target: {value: "Superseded by a later answer"}});
        fireEvent.change(within(dialog).getByLabelText(/Note/), {target: {value: "The respondent changed it."}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Revoke"}));

        await waitFor(() => expect(outcomes.revokeInformationRequestAcceptedFact).toHaveBeenCalledWith(
            "request-a",
            "fact-a",
            {reasonCode: "Superseded by a later answer", narrative: "The respondent changed it."},
            expect.any(String),
        ));
    });

    it("records an original business decision and reconsiders the latest decision of its process", async () =>
    {
        renderPanel();

        fireEvent.click(await screen.findByRole("button", {name: "Record decision"}));
        let dialog = await screen.findByRole("dialog", {name: "Record a business decision"});
        fireEvent.change(within(dialog).getByLabelText(/Process/), {target: {value: "intake"}});
        fireEvent.change(within(dialog).getByLabelText(/Outcome/), {target: {value: "approved"}});
        fireEvent.change(within(dialog).getByLabelText(/Decided/), {target: {value: "2026-09-22T10:00"}});
        fireEvent.change(within(dialog).getByLabelText(/Reason reference/), {target: {value: "Policy 4"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Record decision"}));

        await waitFor(() => expect(outcomes.recordInformationRequestBusinessDecision).toHaveBeenCalledWith(
            "request-a",
            {
                owningProcessKey: "intake",
                outcomeCode: "approved",
                kind: InformationRequestBusinessDecisionKind.ORIGINAL,
                decidedAt: new Date("2026-09-22T10:00").toISOString(),
                reasonReference: "Policy 4",
            },
            expect.any(String),
        ));

        vi.mocked(outcomes.getInformationRequestBusinessDecisions).mockResolvedValue([
            decision({}),
            decision({
                id: "decision-b",
                kind: InformationRequestBusinessDecisionKind.RECONSIDERATION,
                priorDecisionId: "decision-a",
                decisionRevision: 2,
                outcomeCode: "declined",
            }),
        ]);
        cleanup();
        renderPanel();

        const decisions = await screen.findByRole("list", {name: "Business decisions"});
        expect(within(decisions).getAllByRole("listitem")).toHaveLength(2);
        expect(within(decisions).getByText(/Reconsideration/)).toBeTruthy();
        expect(screen.getAllByRole("button", {name: /Reconsider intake/})).toHaveLength(1);
        fireEvent.click(screen.getByRole("button", {name: "Reconsider intake"}));
        dialog = await screen.findByRole("dialog", {name: "Reconsider the intake decision"});
        expect((within(dialog).getByLabelText(/Process/) as HTMLInputElement).disabled).toBe(true);
        fireEvent.change(within(dialog).getByLabelText(/Outcome/), {target: {value: "approved"}});
        fireEvent.change(within(dialog).getByLabelText(/Decided/), {target: {value: "2026-09-24T10:00"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Record decision"}));

        await waitFor(() => expect(outcomes.recordInformationRequestBusinessDecision).toHaveBeenLastCalledWith(
            "request-a",
            {
                owningProcessKey: "intake",
                outcomeCode: "approved",
                kind: InformationRequestBusinessDecisionKind.RECONSIDERATION,
                priorDecisionId: "decision-b",
                decidedAt: new Date("2026-09-24T10:00").toISOString(),
            },
            expect.any(String),
        ));
    });

    it("lists decisions without promotion or recording controls for a caller the server does not let manage outcomes", async () =>
    {
        vi.mocked(outcomes.getInformationRequestAcceptedFacts).mockRejectedValue({errorMessage: "Forbidden"});
        vi.mocked(outcomes.getInformationRequestBusinessDecisions).mockResolvedValue([decision({})]);
        renderPanel();

        expect(await screen.findByRole("list", {name: "Business decisions"})).toBeTruthy();
        expect(screen.queryByRole("button", {name: /Promote/})).toBeNull();
        expect(screen.queryByRole("button", {name: "Record decision"})).toBeNull();
        expect(screen.queryByRole("button", {name: /Reconsider/})).toBeNull();
        expect(screen.queryByRole("region", {name: "Accepted facts"})).toBeNull();
    });

    it("records a privacy correction of a submitted answer against the request's subject", async () =>
    {
        vi.mocked(administration.recordInformationRequestPrivacyRequest).mockResolvedValue({
            id: "privacy-a",
            subjectIdentityRefId: "subject-a",
            requestKind: InformationRequestPrivacyRequestKind.CORRECTION,
            purposeKey: "subject-request",
            policyBasisKey: "rectification",
            state: InformationRequestPrivacyRequestState.COMPLETED,
            recordedByPrincipalKind: "USER",
            recordedByPrincipalId: "user-a",
            recordedAt: "2026-09-27T08:00:00Z",
            targets: [],
        });
        renderPanel(InformationRequestState.CLOSED, "subject-a", true);

        const corrections = await screen.findByRole("region", {name: "Corrections"});
        expect(within(corrections).queryByRole("button", {name: "Correct Second contact time"})).toBeNull();
        fireEvent.click(within(corrections).getByRole("button", {name: "Correct Preferred contact time"}));
        const dialog = await screen.findByRole("dialog", {name: "Correct Preferred contact time"});
        const confirm = within(dialog).getByRole("button", {name: "Record correction"});
        fireEvent.change(within(dialog).getByLabelText(/Corrected value/), {target: {value: "Afternoons"}});
        fireEvent.change(within(dialog).getByLabelText(/Reason/), {target: {value: "Respondent asked"}});
        fireEvent.change(within(dialog).getByLabelText(/Purpose/), {target: {value: "subject-request"}});
        expect((confirm as HTMLButtonElement).disabled).toBe(true);
        fireEvent.change(within(dialog).getByLabelText(/Policy basis/), {target: {value: "rectification"}});
        fireEvent.click(confirm);

        await waitFor(() => expect(administration.recordInformationRequestPrivacyRequest).toHaveBeenCalledWith({
            subjectIdentityRefId: "subject-a",
            requestKind: InformationRequestPrivacyRequestKind.CORRECTION,
            purposeKey: "subject-request",
            policyBasisKey: "rectification",
            correction: {submissionItemId: "item-a", value: "Afternoons", reasonCode: "Respondent asked"},
        }));
        expect(await screen.findByText("The correction was recorded.")).toBeTruthy();
    });

    it("offers no corrections without privacy management or without a named subject", async () =>
    {
        renderPanel(InformationRequestState.CLOSED, "subject-a", false);
        expect(await screen.findByRole("region", {name: "Accepted facts"})).toBeTruthy();
        expect(screen.queryByRole("region", {name: "Corrections"})).toBeNull();
        cleanup();

        renderPanel(InformationRequestState.CLOSED, undefined, true);
        expect(await screen.findByRole("region", {name: "Accepted facts"})).toBeTruthy();
        expect(screen.queryByRole("region", {name: "Corrections"})).toBeNull();
    });

    it("explains a refused promotion and a decision time that has not happened yet", async () =>
    {
        vi.mocked(outcomes.promoteInformationRequestAcceptedFact).mockRejectedValue({
            errorMessage: "Processing for this subject is restricted, so no fact is promoted",
            reasonCode: "INFORMATION_REQUEST_SUBJECT_RESTRICTED",
        });
        renderPanel();

        fireEvent.click(await screen.findByRole("button", {name: "Promote Preferred contact time"}));
        const dialog = await screen.findByRole("dialog", {name: "Promote Preferred contact time"});
        fireEvent.change(within(dialog).getByLabelText(/Purpose/), {target: {value: "contact-details"}});
        fireEvent.change(within(dialog).getByLabelText(/Reuse policy basis/), {target: {value: "policy.contact"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Promote"}));
        expect((await screen.findByRole("alert")).textContent).toContain("Processing for this subject is restricted, so no fact is promoted");

        fireEvent.click(screen.getByRole("button", {name: "Record decision"}));
        const decisionDialog = await screen.findByRole("dialog", {name: "Record a business decision"});
        fireEvent.change(within(decisionDialog).getByLabelText(/Decided/), {target: {value: "2999-01-01T10:00"}});
        expect(within(decisionDialog).getByText("A decision cannot be recorded for a time that has not happened yet.")).toBeTruthy();
    });
});
