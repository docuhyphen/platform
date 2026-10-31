/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as administration from "../../../../services/informationRequestAdministrationService.ts";
import * as outcomes from "../../../../services/informationRequestOutcomeService.ts";
import * as reviews from "../../../../services/informationRequestReviewService.ts";
import {
    FieldValueType,
    InformationRequestAcceptedFactConfidence,
    InformationRequestAcceptedFactConflictState,
    InformationRequestAcceptedFactDto,
    InformationRequestAcceptedFactFreshness,
    InformationRequestAcceptedFactVisibility,
    InformationRequestAuthorActionsDto,
    InformationRequestBusinessDecisionDto,
    InformationRequestBusinessDecisionKind,
    InformationRequestCompletenessItemState,
    InformationRequestExecutionStandingDto,
    InformationRequestExecutionStandingKind,
    InformationRequestPartyDto,
    InformationRequestPrivacyRequestKind,
    InformationRequestPrivacyRequestState,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestReviewDto,
    InformationRequestReviewKind,
    InformationRequestReviewOutcome,
    InformationRequestReviewStageOrdering,
    InformationRequestReviewState,
    InformationRequestShareRoleKey,
    InformationRequestState,
    InformationRequestSubmissionItemDto,
    InformationRequestSubmissionPackageDto,
} from "../../../models/models.tsx";
import {authorActionPermissions} from "../author-workspace-content/authorActionPermissions.ts";
import {everyAuthorAction, noAuthorAction, retainedRecordAuthorActions} from "../testing/authorActions.ts";
import RequestOutcomesPanel from "./RequestOutcomesPanel.tsx";
import {duplicateIds} from "../../shared/testing/duplicateIds.ts";
import {unnamedControls} from "../../shared/testing/unnamedControls.ts";

vi.mock("../../../../services/informationRequestOutcomeService.ts", () => ({
    getInformationRequestSubmissionPackages: vi.fn(),
    getInformationRequestAcceptedFacts: vi.fn(),
    promoteInformationRequestAcceptedFact: vi.fn(),
    revokeInformationRequestAcceptedFact: vi.fn(),
    getInformationRequestBusinessDecisions: vi.fn(),
    recordInformationRequestBusinessDecision: vi.fn(),
}));
vi.mock("../../../../services/informationRequestAdministrationService.ts", () => ({
    recordInformationRequestPrivacyRequest: vi.fn(),
    getInformationRequestPrivacyRequests: vi.fn(),
}));
vi.mock("../../../../services/informationRequestReviewService.ts", () => ({listInformationRequestReviews: vi.fn()}));

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
    submittedAt: Date.parse("2026-09-20T10:00:00Z"),
    submittedByCaller: false,
    withdrawn: false,
    items: [],
    attestations: [],
    supportingEvidenceLinks: [],
    undisclosedItemCount: 0,
    current: true,
    withdrawable: false,
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
    validFrom: Date.parse("2026-09-20T10:00:00Z"),
    conflictState: InformationRequestAcceptedFactConflictState.NONE,
    promotedAt: Date.parse("2026-09-21T10:00:00Z"),
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
    decidedAt: Date.parse("2026-09-22T10:00:00Z"),
    recordedAt: Date.parse("2026-09-22T10:05:00Z"),
    recordedByCaller: true,
    ...overrides,
});

const prompts = {"requirement-a": "Preferred contact time", "requirement-c": "Second contact time"};

const subjectParty = (overrides: Partial<InformationRequestPartyDto> = {}): InformationRequestPartyDto => ({
    id: "party-subject-a",
    informationRequestId: "request-a",
    roleKey: InformationRequestShareRoleKey.SUBJECT,
    active: true,
    subjectIdentityRefId: "subject-a",
    assignedAt: Date.parse("2026-09-19T10:00:00Z"),
    partyRevision: 1,
    partyETag: "\"party-subject-a-1\"",
    label: "Reference 1001",
    ...overrides,
});

const reviewOf = (packageId: string, state: InformationRequestReviewState, outcome?: InformationRequestReviewOutcome): InformationRequestReviewDto => ({
    review: {
        id: `review-${packageId}`,
        informationRequestId: "request-a",
        packageId,
        packageNumber: 2,
        reviewNumber: 1,
        kind: InformationRequestReviewKind.INITIAL,
        state,
        openedAt: Date.parse("2026-09-20T11:00:00Z"),
        reviewETag: "\"review-1\"",
    },
    reviewStageOrdering: InformationRequestReviewStageOrdering.SEQUENTIAL,
    stages: [],
    items: outcome ? [{
        submissionItemId: "item-r",
        requirementId: "requirement-c",
        requirementKey: "second_time",
        requirementType: InformationRequestRequirementType.FIELD,
        occurrencePath: "",
        reviewed: true,
        finalOutcome: outcome,
        contentVisible: true,
        waiverPermitted: false,
        evidence: [],
    }] : [],
    assignments: [],
    decisions: [],
    findings: [],
    comments: [],
    remediations: [],
    worksheets: [],
    canManage: true,
    earlierFindings: [],
    earlierRemediations: [],
    canReconsider: false,
    canComment: false,
    itemsAwaitingStepUp: 0,
});

const ACTIVE: InformationRequestExecutionStandingDto = {kind: InformationRequestExecutionStandingKind.ACTIVE};

const renderPanel = (
    state = InformationRequestState.CLOSED,
    subjects: InformationRequestPartyDto[] = [subjectParty()],
    canCorrect = false,
    standing = ACTIVE,
    actions: InformationRequestAuthorActionsDto = everyAuthorAction,
) => render(
    <RequestOutcomesPanel requestId={"request-a"}
                          permissions={authorActionPermissions(state, standing, actions).outcomes}
                          prompts={prompts}
                          promptsByKey={{}}
                          subjects={subjects}
                          canCorrect={canCorrect}/>,
);

describe("RequestOutcomesPanel", () =>
{
    beforeAll(() =>
    {
        vi.stubGlobal("ResizeObserver", class
        {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
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
        vi.mocked(reviews.listInformationRequestReviews).mockResolvedValue([]);
        vi.mocked(administration.getInformationRequestPrivacyRequests).mockResolvedValue([]);
    });

    afterEach(cleanup);

    it.each(["promotion", "revocation", "decision", "correction"] as const)("keeps a refused %s dialog and its entered fields", async kind => {
        const refusal = {errorMessage: "The current policy refuses this change"};
        vi.mocked(outcomes.promoteInformationRequestAcceptedFact).mockRejectedValue(refusal);
        vi.mocked(outcomes.revokeInformationRequestAcceptedFact).mockRejectedValue(refusal);
        vi.mocked(outcomes.recordInformationRequestBusinessDecision).mockRejectedValue(refusal);
        vi.mocked(administration.recordInformationRequestPrivacyRequest).mockRejectedValue(refusal);
        if (kind === "revocation") vi.mocked(outcomes.getInformationRequestAcceptedFacts).mockResolvedValue([fact({})]);
        const cases = {
            promotion: {
                open: "Promote Preferred contact time", title: "Promote Preferred contact time", confirm: "Promote",
                fields: [[/Purpose/, "record.review"], [/Reuse policy basis/, "policy.review"]] as const
            },
            revocation: {
                open: "Revoke Preferred contact time for contact details",
                title: "Revoke this accepted fact",
                confirm: "Revoke",
                fields: [[/Reason/, "outdated"], [/Note/, "Keep this explanation"]] as const
            },
            decision: {
                open: "Record decision", title: "Record a business decision", confirm: "Record decision",
                fields: [[/Process/, "record.review"], [/Outcome/, "complete"]] as const
            },
            correction: {
                open: "Correct Preferred contact time",
                title: "Correct Preferred contact time",
                confirm: "Record correction",
                fields: [[/Corrected value/, "Evenings"], [/Reason/, "corrected"], [/Purpose/, "record.review"], [/Policy basis/, "policy.review"]] as const
            },
        };
        const scenario = cases[kind];
        renderPanel(InformationRequestState.CLOSED, [subjectParty()], true);
        fireEvent.click(await screen.findByRole("button", {name: scenario.open}));
        const dialog = await screen.findByRole("dialog", {name: scenario.title});
        scenario.fields.forEach(([label, value]) => fireEvent.change(within(dialog).getByLabelText(label), {target: {value}}));
        fireEvent.click(within(dialog).getByRole("button", {name: scenario.confirm}));

        await waitFor(() => expect(screen.queryAllByText(refusal.errorMessage).length).toBeGreaterThan(0));
        const kept = screen.getByRole("dialog", {name: scenario.title});
        expect((within(kept).getByLabelText(scenario.fields[0][0]) as HTMLInputElement).value).toBe(scenario.fields[0][1]);
        expect(within(kept).getByRole("alert").textContent).toContain(refusal.errorMessage);
    });

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

    it("says why a corrected answer cannot be promoted instead of offering Promote", async () => {
        vi.mocked(administration.getInformationRequestPrivacyRequests).mockResolvedValue([{
            id: "privacy-a",
            subjectIdentityRefId: "subject-a",
            requestKind: InformationRequestPrivacyRequestKind.CORRECTION,
            purposeKey: "record.review",
            policyBasisKey: "policy.review",
            state: InformationRequestPrivacyRequestState.COMPLETED,
            recordedByPrincipalKind: "USER",
            recordedByPrincipalId: "user-a",
            recordedAt: Date.parse("2026-09-23T10:00:00Z"),
            targets: [],
            correction: {
                id: "correction-a",
                requestId: "request-a",
                packageId: "package-a",
                submissionItemId: "item-a",
                reasonCode: "corrected",
                recordedAt: Date.parse("2026-09-23T10:00:00Z"),
            },
        }]);
        renderPanel(InformationRequestState.CLOSED, [subjectParty()], true);

        expect(await screen.findByText("This answer was corrected, so it cannot be promoted.")).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Promote Preferred contact time"})).toBeNull();
        expect(screen.getAllByText("Mornings").length).toBeGreaterThan(0);
    });

    it("says that a restricted subject's answers cannot be promoted instead of offering Promote", async () => {
        renderPanel(InformationRequestState.CLOSED, [subjectParty({processingRestricted: true})]);

        expect(await screen.findByText("Processing for this subject is restricted, so no fact can be promoted.")).toBeTruthy();
        expect(screen.queryByRole("button", {name: /^Promote /})).toBeNull();
    });

    it("names an answer by its Template prompt for a manager who answers none of the request's items", async () => {
        render(
            <RequestOutcomesPanel requestId={"request-a"}
                                  permissions={authorActionPermissions(InformationRequestState.CLOSED, ACTIVE, everyAuthorAction).outcomes}
                                  prompts={{}}
                                  promptsByKey={{contact_time: "Preferred contact time"}}
                                  subjects={[subjectParty()]}
                                  canCorrect={false}/>,
        );

        expect(await screen.findByRole("button", {name: "Promote Preferred contact time"})).toBeTruthy();
    });

    it("pins only conforming evidence that supports the promoted answer in the same package", async () =>
    {
        vi.mocked(outcomes.getInformationRequestSubmissionPackages).mockResolvedValue([
            submission({
                supportingEvidenceLinks: [{
                    supportedRequirementId: "requirement-a",
                    supportingRequirementId: "requirement-b"
                }],
                items: [
                    item({}),
                    item({
                        id: "item-b",
                        requirementId: "requirement-b",
                        requirementKey: "supporting_file",
                        requirementType: InformationRequestRequirementType.DOCUMENT,
                        fieldValue: undefined,
                        evidence: [
                            {
                                artifactId: "artifact-b",
                                evidenceVersionId: "version-kept",
                                versionNumber: 2,
                                conformance: "CONFORMING"
                            },
                            {
                                artifactId: "artifact-b",
                                evidenceVersionId: "version-refused",
                                versionNumber: 1,
                                conformance: "NONCONFORMING"
                            },
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
        renderPanel(InformationRequestState.CLOSED, [subjectParty()], true);
        fireEvent.click(await screen.findByRole("button", {name: "Record decision"}));
        await screen.findByRole("dialog", {name: "Record a business decision"});

        expect(unnamedControls(document.body)).toEqual([]);

        expect(duplicateIds(document.body)).toEqual([]);
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
                revokedAt: Date.parse("2026-09-23T10:00:00Z"),
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

    it.each([
        InformationRequestState.CANCELLED,
        InformationRequestState.SUPERSEDED,
        InformationRequestState.EXPIRED,
    ])("still revokes a promoted fact of a %s request but offers no new promotion", async state => {
        vi.mocked(outcomes.getInformationRequestAcceptedFacts).mockResolvedValue([fact({})]);
        renderPanel(state);

        const facts = await screen.findByRole("list", {name: "Promoted facts"});
        expect(document.getElementById("information-request-promotable-answers-title")).toBeNull();
        fireEvent.click(within(facts).getByRole("button", {name: "Revoke Preferred contact time for contact details"}));
        const dialog = await screen.findByRole("dialog", {name: "Revoke this accepted fact"});
        fireEvent.change(within(dialog).getByLabelText(/Reason/), {target: {value: "Found to be wrong"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Revoke"}));

        await waitFor(() => expect(outcomes.revokeInformationRequestAcceptedFact).toHaveBeenCalledWith(
            "request-a",
            "fact-a",
            {reasonCode: "Found to be wrong"},
            expect.any(String),
        ));
    });

    it("offers no revocation on a draft request", async () => {
        vi.mocked(outcomes.getInformationRequestAcceptedFacts).mockResolvedValue([fact({})]);
        renderPanel(InformationRequestState.DRAFT);

        const facts = await screen.findByRole("list", {name: "Promoted facts"});
        expect(within(facts).queryByRole("button", {name: /Revoke/})).toBeNull();
    });

    it.each([
        InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED,
        InformationRequestExecutionStandingKind.EXECUTION_GRANT_REVOKED,
    ])("offers no promotion or decision while the request stands %s and still revokes a promoted fact", async kind => {
        vi.mocked(outcomes.getInformationRequestAcceptedFacts).mockResolvedValue([fact({})]);
        vi.mocked(outcomes.getInformationRequestBusinessDecisions).mockResolvedValue([decision({})]);
        renderPanel(InformationRequestState.CLOSED, undefined, false, {kind});

        const facts = await screen.findByRole("list", {name: "Promoted facts"});
        expect(within(facts).getByRole("button", {name: "Revoke Preferred contact time for contact details"})).toBeTruthy();
        expect(await screen.findByRole("list", {name: "Business decisions"})).toBeTruthy();
        expect(screen.queryByText("Answers you can promote")).toBeNull();
        expect(screen.queryByRole("button", {name: /^Promote /})).toBeNull();
        expect(screen.queryByRole("button", {name: "Record decision"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Reconsider intake"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Appeal intake"})).toBeNull();
    });

    it("keeps a closed request's facts revocable after its Exchange ends but offers no promotion or decision the server refuses", async () => {
        vi.mocked(outcomes.getInformationRequestAcceptedFacts).mockResolvedValue([fact({})]);
        vi.mocked(outcomes.getInformationRequestBusinessDecisions).mockResolvedValue([decision({})]);
        renderPanel(InformationRequestState.CLOSED, undefined, false, ACTIVE, retainedRecordAuthorActions);

        const facts = await screen.findByRole("list", {name: "Promoted facts"});
        expect(await screen.findByRole("list", {name: "Business decisions"})).toBeTruthy();
        expect(screen.queryByText("Answers you can promote")).toBeNull();
        expect(screen.queryByRole("button", {name: /^Promote /})).toBeNull();
        expect(screen.queryByRole("button", {name: "Record decision"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Reconsider intake"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Appeal intake"})).toBeNull();
        fireEvent.click(within(facts).getByRole("button", {name: "Revoke Preferred contact time for contact details"}));
        const dialog = await screen.findByRole("dialog", {name: "Revoke this accepted fact"});
        fireEvent.change(within(dialog).getByLabelText(/Reason/), {target: {value: "Found to be wrong"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Revoke"}));

        await waitFor(() => expect(outcomes.revokeInformationRequestAcceptedFact).toHaveBeenCalledWith(
            "request-a",
            "fact-a",
            {reasonCode: "Found to be wrong"},
            expect.any(String),
        ));
    });

    it("keeps the recorded outcomes readable but offers no outcome action at all when the server refuses every one", async () => {
        vi.mocked(outcomes.getInformationRequestAcceptedFacts).mockResolvedValue([fact({})]);
        vi.mocked(outcomes.getInformationRequestBusinessDecisions).mockResolvedValue([decision({})]);
        renderPanel(InformationRequestState.IN_PROGRESS, undefined, false, ACTIVE, noAuthorAction);

        const facts = await screen.findByRole("list", {name: "Promoted facts"});
        expect(within(facts).getByText(/Mornings/)).toBeTruthy();
        expect(await screen.findByRole("list", {name: "Business decisions"})).toBeTruthy();
        expect(within(facts).queryByRole("button", {name: /Revoke/})).toBeNull();
        expect(screen.queryByRole("button", {name: /^Promote /})).toBeNull();
        expect(screen.queryByRole("button", {name: "Record decision"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Reconsider intake"})).toBeNull();
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
                decidedAt: new Date("2026-09-22T10:00").getTime(),
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
                decidedAt: new Date("2026-09-24T10:00").getTime(),
            },
            expect.any(String),
        ));
    });

    it("lists decisions without promotion or recording controls for a caller the server does not let manage outcomes", async () =>
    {
        vi.mocked(outcomes.getInformationRequestAcceptedFacts).mockRejectedValue({
            errorMessage: "Forbidden",
            reasonCode: "INFORMATION_REQUEST_FORBIDDEN"
        });
        vi.mocked(outcomes.getInformationRequestBusinessDecisions).mockResolvedValue([decision({})]);
        renderPanel();

        expect(await screen.findByRole("list", {name: "Business decisions"})).toBeTruthy();
        expect(screen.queryByRole("button", {name: /Promote/})).toBeNull();
        expect(screen.queryByRole("button", {name: "Record decision"})).toBeNull();
        expect(screen.queryByRole("button", {name: /Reconsider/})).toBeNull();
        expect(screen.queryByRole("region", {name: "Accepted facts"})).toBeNull();
    });

    it("shows no business decisions to a caller the server does not let read them and keeps the rest of the outcomes", async () => {
        vi.mocked(outcomes.getInformationRequestAcceptedFacts).mockRejectedValue({
            errorMessage: "Forbidden",
            reasonCode: "INFORMATION_REQUEST_FORBIDDEN"
        });
        vi.mocked(outcomes.getInformationRequestBusinessDecisions).mockRejectedValue({
            errorMessage: "Forbidden",
            reasonCode: "INFORMATION_REQUEST_FORBIDDEN"
        });
        renderPanel();

        await waitFor(() => expect(outcomes.getInformationRequestBusinessDecisions).toHaveBeenCalled());
        await waitFor(() => expect(screen.queryByText("Loading outcomes")).toBeNull());
        expect(screen.queryByRole("list", {name: "Business decisions"})).toBeNull();
        expect(document.getElementById("information-request-outcomes-error")).toBeNull();
    });

    it("states a refused read of the accepted facts and decisions with Try again and shows them once a retry loads", async () => {
        vi.mocked(outcomes.getInformationRequestAcceptedFacts).mockRejectedValueOnce({errorMessage: "The accepted facts could not be read."});
        vi.mocked(outcomes.getInformationRequestBusinessDecisions)
            .mockRejectedValueOnce({errorMessage: "The decisions could not be read."})
            .mockResolvedValue([decision({})]);
        renderPanel();

        await waitFor(() => expect(document.getElementById("information-request-outcomes-load-error")?.textContent)
            .toContain("The accepted facts could not be read."));
        expect(screen.queryByRole("list", {name: "Business decisions"})).toBeNull();
        fireEvent.click(within(document.getElementById("information-request-outcomes-load-error") as HTMLElement)
            .getByRole("button", {name: "Try again"}));
        expect(await screen.findByRole("list", {name: "Business decisions"})).toBeTruthy();
        expect(screen.getByRole("region", {name: "Accepted facts"})).toBeTruthy();
        expect(document.getElementById("information-request-outcomes-load-error")).toBeNull();
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
            recordedAt: Date.parse("2026-09-27T08:00:00Z"),
            targets: [],
        });
        renderPanel(InformationRequestState.CLOSED, [subjectParty()], true);

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
        }, expect.any(String)));
        expect(await screen.findByText("The correction was recorded.")).toBeTruthy();
    });

    it("shows the note of an answer whose value the server sends as null among the corrections", async () => {
        vi.mocked(outcomes.getInformationRequestSubmissionPackages).mockResolvedValue([
            submission({
                items: [
                    item({
                        id: "item-n",
                        requirementId: "requirement-c",
                        fieldValue: null,
                        narrative: "Called on the second working day"
                    }),
                    item({id: "item-z", requirementId: "requirement-a", fieldValue: 0, narrative: null}),
                ],
            }),
        ]);
        renderPanel(InformationRequestState.CLOSED, [subjectParty()], true);

        await screen.findByRole("region", {name: "Corrections"});
        expect(document.getElementById("information-request-item-correction-item-n-content")?.textContent)
            .toBe("Called on the second working day");
        expect(document.getElementById("information-request-item-correction-item-z-content")?.textContent).toBe("0");
    });

    it("offers no corrections without privacy management or without a named subject", async () =>
    {
        renderPanel(InformationRequestState.CLOSED, [subjectParty()], false);
        expect(await screen.findByRole("region", {name: "Accepted facts"})).toBeTruthy();
        expect(screen.queryByRole("region", {name: "Corrections"})).toBeNull();
        cleanup();

        renderPanel(InformationRequestState.CLOSED, [], true);
        expect(await screen.findByRole("region", {name: "Accepted facts"})).toBeTruthy();
        expect(screen.queryByRole("region", {name: "Corrections"})).toBeNull();
    });

    it("offers Promote only for an answer whose package review accepted it", async () => {
        vi.mocked(outcomes.getInformationRequestSubmissionPackages).mockResolvedValue([
            submission({items: [item({})]}),
            submission({
                id: "package-r", packageNumber: 2, reviewRequired: true,
                items: [item({
                    id: "item-r",
                    requirementId: "requirement-c",
                    requirementKey: "second_time",
                    fieldValue: "Evenings"
                })]
            }),
        ]);
        vi.mocked(reviews.listInformationRequestReviews).mockResolvedValue([reviewOf("package-r", InformationRequestReviewState.IN_REVIEW)]);
        renderPanel();

        expect(await screen.findByRole("button", {name: "Promote Preferred contact time"})).toBeTruthy();
        expect(await screen.findByText("Its review has not accepted it yet.")).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Promote Second contact time"})).toBeNull();
        cleanup();

        vi.mocked(reviews.listInformationRequestReviews)
            .mockResolvedValue([reviewOf("package-r", InformationRequestReviewState.SATISFIED, InformationRequestReviewOutcome.CHANGES_REQUIRED)]);
        renderPanel();
        expect(await screen.findByText("Its review has not accepted it yet.")).toBeTruthy();
        cleanup();

        vi.mocked(reviews.listInformationRequestReviews)
            .mockResolvedValue([reviewOf("package-r", InformationRequestReviewState.SATISFIED, InformationRequestReviewOutcome.SATISFIED)]);
        renderPanel();
        expect(await screen.findByRole("button", {name: "Promote Second contact time"})).toBeTruthy();
    });

    it.each([
        ["no subject", [] as InformationRequestPartyDto[], "Promotion needs the request to name a single subject."],
        ["two subjects", [subjectParty(), subjectParty({id: "party-subject-b", subjectIdentityRefId: "subject-b"})],
            "Promotion needs the request to name a single subject."],
        ["a subject named after the submission", [subjectParty({assignedAt: Date.parse("2026-09-21T10:00:00Z")})],
            "The subject was named after this answer was submitted, so it cannot be promoted."],
    ])("explains instead of offering Promote when the request names %s", async (_case, subjects, reason) => {
        renderPanel(InformationRequestState.CLOSED, subjects);

        expect(await screen.findByText(reason)).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Promote Preferred contact time"})).toBeNull();
    });

    it("names the purposes an answer is already a fact for and refuses the same purpose again", async () => {
        vi.mocked(outcomes.getInformationRequestAcceptedFacts).mockResolvedValue([
            fact({}),
            fact({id: "fact-r", purposeKey: "contact-review", revoked: true}),
        ]);
        renderPanel();

        expect(await screen.findByText("Promoted for contact-details")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Promote Preferred contact time"}));
        const dialog = await screen.findByRole("dialog", {name: "Promote Preferred contact time"});
        fireEvent.change(within(dialog).getByLabelText(/Purpose/), {target: {value: "contact-details"}});
        fireEvent.change(within(dialog).getByLabelText(/Reuse policy basis/), {target: {value: "policy.contact"}});

        expect(within(dialog).getByText("This answer is already a current fact for this purpose.")).toBeTruthy();
        expect((within(dialog).getByRole("button", {name: "Promote"}) as HTMLButtonElement).disabled).toBe(true);
        fireEvent.change(within(dialog).getByLabelText(/Purpose/), {target: {value: "contact-review"}});
        expect((within(dialog).getByRole("button", {name: "Promote"}) as HTMLButtonElement).disabled).toBe(false);
    });

    it("lets the author choose the subject of a correction when several are named, and lists recorded corrections", async () => {
        vi.mocked(administration.getInformationRequestPrivacyRequests).mockImplementation(async subjectIdentityRefId =>
            subjectIdentityRefId === "subject-b" ? [{
                id: "privacy-b",
                subjectIdentityRefId: "subject-b",
                requestKind: InformationRequestPrivacyRequestKind.CORRECTION,
                purposeKey: "subject-request",
                policyBasisKey: "rectification",
                state: InformationRequestPrivacyRequestState.COMPLETED,
                recordedByPrincipalKind: "USER",
                recordedByPrincipalId: "user-a",
                recordedAt: Date.parse("2026-09-27T08:00:00Z"),
                targets: [],
                correction: {
                    id: "correction-b",
                    requestId: "request-a",
                    packageId: "package-a",
                    submissionItemId: "item-a",
                    reasonCode: "respondent-asked",
                    recordedAt: Date.parse("2026-09-27T08:00:00Z"),
                },
            }] : []);
        const second = subjectParty({
            id: "party-subject-b",
            subjectIdentityRefId: "subject-b",
            label: "Reference 2002"
        });
        renderPanel(InformationRequestState.CLOSED, [subjectParty(), second], true);

        const corrections = await screen.findByRole("region", {name: "Corrections"});
        expect(await within(corrections).findByText(/Corrected for Reference 2002/)).toBeTruthy();
        fireEvent.click(within(corrections).getByRole("button", {name: "Correct Preferred contact time"}));
        const dialog = await screen.findByRole("dialog", {name: "Correct Preferred contact time"});
        fireEvent.change(within(dialog).getByLabelText(/Corrected value/), {target: {value: "Afternoons"}});
        fireEvent.change(within(dialog).getByLabelText(/Reason/), {target: {value: "respondent-asked"}});
        fireEvent.change(within(dialog).getByLabelText(/Purpose/), {target: {value: "subject-request"}});
        fireEvent.change(within(dialog).getByLabelText(/Policy basis/), {target: {value: "rectification"}});
        const confirm = within(dialog).getByRole("button", {name: "Record correction"}) as HTMLButtonElement;
        expect(confirm.disabled).toBe(true);
        fireEvent.change(within(dialog).getByLabelText(/Subject/), {target: {value: "subject-b"}});
        fireEvent.click(confirm);

        await waitFor(() => expect(administration.recordInformationRequestPrivacyRequest).toHaveBeenCalledWith(
            expect.objectContaining({subjectIdentityRefId: "subject-b"}), expect.any(String)));
    });

    it("explains a refused promotion and a decision time that has not happened yet", {timeout: 15000}, async () =>
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
        fireEvent.click(within(dialog).getByRole("button", {name: "Cancel"}));
        await waitFor(() => expect(screen.queryByRole("dialog", {name: "Promote Preferred contact time"})).toBeNull());

        fireEvent.click(await screen.findByRole("button", {name: "Record decision"}, {timeout: 5000}));
        const decisionDialog = await screen.findByRole("dialog", {name: "Record a business decision"});
        fireEvent.change(within(decisionDialog).getByLabelText(/Decided/), {target: {value: "2999-01-01T10:00"}});
        expect(within(decisionDialog).getByText("A decision cannot be recorded for a time that has not happened yet.")).toBeTruthy();
    });
});
