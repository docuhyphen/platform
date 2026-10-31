/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {MemoryRouter} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as authoring from "../../../../services/informationRequestAuthoringService.ts";
import {
    InformationRequestAuthorActionsDto,
    InformationRequestContributorRole,
    InformationRequestDto,
    InformationRequestEvidenceAttributeRequirement,
    InformationRequestExecutionStandingKind,
    InformationRequestLineageKind,
    InformationRequestOwnerType,
    InformationRequestRecurrenceDto,
    InformationRequestRecurrenceUnit,
    InformationRequestRefreshRuleDto,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseMode,
    InformationRequestReviewPolicy,
    InformationRequestState,
    InformationRequestTemplateEvidencePolicyDto,
    InformationRequestTemplateRequirementDto,
    InformationRequestTemplateSectionDto,
} from "../../../models/models.tsx";
import {authorActionPermissions} from "../author-workspace-content/authorActionPermissions.ts";
import {everyAuthorAction, retainedRecordAuthorActions} from "../testing/authorActions.ts";
import FollowUpPanel from "./FollowUpPanel.tsx";

vi.mock("../../../../services/informationRequestAuthoringService.ts", () => ({
    getInformationRequestLineage: vi.fn(),
    defineInformationRequestRecurrence: vi.fn(),
    createNextInformationRequestOccurrence: vi.fn(),
    requestInformationRequestSupplement: vi.fn(),
    defineInformationRequestRefreshRule: vi.fn(),
    createInformationRequestRefresh: vi.fn(),
}));

const documentRequirement = (
    requirementKey: string,
    prompt: string,
    expiryDateRequirement: InformationRequestEvidenceAttributeRequirement,
): InformationRequestTemplateRequirementDto => ({
    id: `binding-${requirementKey}`,
    templateRequirementId: `requirement-${requirementKey}`,
    requirementKey,
    requirementType: InformationRequestRequirementType.DOCUMENT,
    prompt,
    responseMode: InformationRequestResponseMode.PROVIDE,
    requiredness: InformationRequestRequiredness.REQUIRED,
    contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
    permittedDispositions: [InformationRequestResponseDisposition.PROVIDED],
    substituteRequirementKeys: [],
    supportingEvidenceRequirementKeys: [],
    evidencePolicy: {expiryDateRequirement} as InformationRequestTemplateEvidencePolicyDto,
});

const sections: InformationRequestTemplateSectionDto[] = [{
    id: "section-a",
    sectionKey: "records",
    title: "Records",
    requirements: [
        documentRequirement("supporting-record", "Provide the supporting record", InformationRequestEvidenceAttributeRequirement.REQUIRED),
        documentRequirement("cover-note", "Provide the cover note", InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED),
        documentRequirement("dated-statement", "Provide the dated statement", InformationRequestEvidenceAttributeRequirement.OPTIONAL),
    ],
}];

const request: InformationRequestDto = {
    id: "request-a",
    exchangeId: "exchange-a",
    templateVersionId: "version-a",
    ownerType: InformationRequestOwnerType.ORGANIZATION,
    state: InformationRequestState.CLOSED,
    gatesExchangeClosure: true,
    aggregateRevision: 7,
    createdAt: Date.parse("2026-09-01T08:00:00Z"),
    updatedAt: Date.parse("2026-09-20T08:00:00Z"),
    requestETag: "\"request-7\"",
    executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
    conditionEvaluations: [],
};

const renderPanel = (
    canCreate = true,
    state = InformationRequestState.CLOSED,
    actions: InformationRequestAuthorActionsDto = everyAuthorAction,
) => render(
    <MemoryRouter>
        <FollowUpPanel request={{...request, state}}
                       sections={sections}
                       permissions={authorActionPermissions(
                           state,
                           {kind: canCreate ? InformationRequestExecutionStandingKind.ACTIVE : InformationRequestExecutionStandingKind.NEW_WORK_UNAVAILABLE},
                           actions,
                       ).followUps}
                       onChanged={vi.fn()}/>
    </MemoryRouter>,
);

const lineageWithRefreshRule = () => vi.mocked(authoring.getInformationRequestLineage).mockResolvedValue({
    informationRequestId: "request-a",
    successors: [],
    refreshRules: [{
        id: "rule-a",
        informationRequestId: "request-a",
        requirementKey: "supporting-record",
        leadDays: 30,
        createdAt: Date.parse("2026-09-12T08:00:00Z"),
        refreshDueOn: "2026-10-09",
        refreshDue: true,
    }],
});

const recurrence = (): InformationRequestRecurrenceDto => ({
    id: "recurrence-a",
    originRequestId: "request-a",
    intervalUnit: InformationRequestRecurrenceUnit.MONTH,
    intervalCount: 1,
    firstDueAt: Date.parse("2026-09-10T08:00:00Z"),
    maximumOccurrences: 2,
    createdAt: Date.parse("2026-09-01T08:00:00Z"),
});

describe("FollowUpPanel", () =>
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
        vi.mocked(authoring.defineInformationRequestRecurrence).mockResolvedValue({} as never);
        vi.mocked(authoring.createNextInformationRequestOccurrence).mockResolvedValue({} as never);
        vi.mocked(authoring.requestInformationRequestSupplement).mockResolvedValue({} as never);
    });

    afterEach(cleanup);

    it("schedules a recurrence under the request ETag", async () =>
    {
        vi.mocked(authoring.getInformationRequestLineage).mockResolvedValue({
            informationRequestId: "request-a",
            successors: []
        });
        renderPanel();

        fireEvent.change(await screen.findByLabelText("Repeat every"), {target: {value: "3"}});
        fireEvent.change(screen.getByLabelText("Unit"), {target: {value: InformationRequestRecurrenceUnit.MONTH}});
        fireEvent.change(screen.getByLabelText("First due"), {target: {value: "2026-10-01T08:00"}});
        fireEvent.change(screen.getByLabelText("Stop after"), {target: {value: "4"}});
        fireEvent.click(screen.getByRole("button", {name: "Schedule"}));

        await waitFor(() => expect(authoring.defineInformationRequestRecurrence).toHaveBeenCalledWith(
            "request-a",
            {
                intervalUnit: InformationRequestRecurrenceUnit.MONTH,
                intervalCount: 3,
                firstDueAt: new Date("2026-10-01T08:00").toISOString(),
                maximumOccurrences: 4,
            },
            "\"request-7\"",
            expect.any(String),
        ));
    });

    it("offers Schedule only for an interval and first due time the server accepts", async () => {
        vi.mocked(authoring.getInformationRequestLineage).mockResolvedValue({
            informationRequestId: "request-a",
            successors: []
        });
        renderPanel();
        const count = await screen.findByLabelText("Repeat every");
        const firstDue = screen.getByLabelText("First due");
        const scheduleDisabled = () => (screen.getByRole("button", {name: "Schedule"}) as HTMLButtonElement).disabled;

        fireEvent.change(firstDue, {target: {value: "2026-10-01T08:00"}});
        fireEvent.change(count, {target: {value: "1001"}});
        expect(scheduleDisabled()).toBe(true);

        fireEvent.change(count, {target: {value: "1000"}});
        expect(scheduleDisabled()).toBe(false);

        fireEvent.change(firstDue, {target: {value: "1969-06-01T08:00"}});
        expect(scheduleDisabled()).toBe(true);
        expect(authoring.defineInformationRequestRecurrence).not.toHaveBeenCalled();
    });

    it("creates the next recurring request once it is due and lists follow-up requests", async () =>
    {
        vi.mocked(authoring.getInformationRequestLineage).mockResolvedValue({
            informationRequestId: "request-a",
            successors: [{
                id: "lineage-a",
                lineageKind: InformationRequestLineageKind.RECURRENCE,
                sourceRequestId: "request-a",
                successorRequestId: "request-b",
                recurrenceId: "recurrence-a",
                recurrenceSequence: 1,
                createdAt: Date.parse("2026-09-10T08:00:00Z"),
            }],
            recurrence: {
                id: "recurrence-a",
                originRequestId: "request-a",
                intervalUnit: InformationRequestRecurrenceUnit.MONTH,
                intervalCount: 1,
                firstDueAt: Date.parse("2026-09-10T08:00:00Z"),
                createdAt: Date.parse("2026-09-01T08:00:00Z"),
            },
            nextOccurrenceDueAt: Date.parse("2026-09-26T08:00:00Z"),
            nextOccurrenceCreatable: true,
        });
        renderPanel();

        expect(await screen.findByText("Recurring request 1")).toBeTruthy();
        expect(screen.getByText(/^Repeats every 1 month/)).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Create the next request"}));

        await waitFor(() => expect(authoring.createNextInformationRequestOccurrence)
            .toHaveBeenCalledWith("request-a", "recurrence-a", expect.any(String)));
    });

    it("shows an occurrence's series and creates the next request through the series origin when the server allows it", async () => {
        vi.mocked(authoring.getInformationRequestLineage).mockResolvedValue({
            informationRequestId: "request-a",
            successors: [],
            series: {...recurrence(), originRequestId: "request-origin"},
            nextOccurrenceDueAt: Date.parse("2026-09-26T08:00:00Z"),
            nextOccurrenceCreatable: true,
        });
        renderPanel(true, InformationRequestState.ISSUED);

        expect(await screen.findByText(/^This request is part of a series\. Repeats every 1 month/)).toBeTruthy();
        expect(document.getElementById("information-request-recurrence-form")).toBeNull();
        fireEvent.click(screen.getByRole("button", {name: "Create the next request"}));

        await waitFor(() => expect(authoring.createNextInformationRequestOccurrence)
            .toHaveBeenCalledWith("request-origin", "recurrence-a", expect.any(String)));
    });

    it("offers the next request of a cancelled origin's series only when the server says the series can continue", async () => {
        vi.mocked(authoring.getInformationRequestLineage).mockResolvedValue({
            informationRequestId: "request-a",
            successors: [],
            recurrence: recurrence(),
            nextOccurrenceDueAt: Date.parse("2026-09-26T08:00:00Z"),
            nextOccurrenceCreatable: true,
        });
        renderPanel(true, InformationRequestState.CANCELLED);

        expect(await screen.findByRole("button", {name: "Create the next request"})).toBeTruthy();
        cleanup();

        vi.mocked(authoring.getInformationRequestLineage).mockResolvedValue({
            informationRequestId: "request-a",
            successors: [],
            recurrence: recurrence(),
            nextOccurrenceDueAt: Date.parse("2026-09-26T08:00:00Z"),
            nextOccurrenceCreatable: false,
        });
        renderPanel(true, InformationRequestState.ISSUED);
        expect(await screen.findByText(/^Repeats every 1 month/)).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Create the next request"})).toBeNull();
    });

    it("states when each refresh rule falls due and offers Refresh now only for a rule that is due", async () => {
        const rule = (requirementKey: string, state: Partial<InformationRequestRefreshRuleDto>): InformationRequestRefreshRuleDto => ({
            id: `rule-${requirementKey}`,
            informationRequestId: "request-a",
            requirementKey,
            leadDays: 30,
            createdAt: Date.parse("2026-09-12T08:00:00Z"),
            ...state,
        });
        vi.mocked(authoring.getInformationRequestLineage).mockResolvedValue({
            informationRequestId: "request-a",
            successors: [],
            refreshRules: [
                rule("supporting-record", {refreshDueOn: "2026-10-09", refreshDue: true}),
                rule("dated-statement", {refreshDueOn: "2026-12-28"}),
                rule("cover-note", {refreshDueOn: "2026-10-01", refreshed: true}),
                rule("other-record", {}),
            ],
        });
        renderPanel(true, InformationRequestState.ISSUED);

        expect(await screen.findByText("Due for refresh now.")).toBeTruthy();
        expect(screen.getByText("Due for refresh from 2026-12-28.")).toBeTruthy();
        expect(screen.getByText("Refreshed for the latest submission.")).toBeTruthy();
        expect(screen.getByText("No submitted file of this document has an expiry date yet.")).toBeTruthy();
        expect(screen.getAllByRole("button", {name: /^Refresh .* now$/}).map(button => button.getAttribute("aria-label")))
            .toEqual(["Refresh Provide the supporting record now"]);
    });

    it("says every scheduled request was created only when the server reports the series finished", async () => {
        vi.mocked(authoring.getInformationRequestLineage).mockResolvedValue({
            informationRequestId: "request-a",
            successors: [],
            recurrence: recurrence(),
            nextOccurrenceDueAt: null,
            everyOccurrenceCreated: true,
        });
        renderPanel();

        expect(await screen.findByText("Every scheduled request has been created.")).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Create the next request"})).toBeNull();
    });

    it("states no next due time when the server withholds it and lets the server answer whether the next request is due", async () => {
        vi.mocked(authoring.getInformationRequestLineage).mockResolvedValue({
            informationRequestId: "request-a",
            successors: [],
            recurrence: recurrence(),
            nextOccurrenceDueAt: null,
            everyOccurrenceCreated: false,
            nextOccurrenceCreatable: true,
        });
        renderPanel();

        expect(await screen.findByText(/^Repeats every 1 month/)).toBeTruthy();
        expect(screen.queryByText("Every scheduled request has been created.")).toBeNull();
        expect(screen.queryByText(/^Next request due/)).toBeNull();
        expect(document.getElementById("information-request-follow-up-recurrence-next")).toBeNull();
        fireEvent.click(screen.getByRole("button", {name: "Create the next request"}));

        await waitFor(() => expect(authoring.createNextInformationRequestOccurrence)
            .toHaveBeenCalledWith("request-a", "recurrence-a", expect.any(String)));
    });

    it("keeps the follow-up history but offers no new follow-ups while new work is unavailable", async () =>
    {
        vi.mocked(authoring.getInformationRequestLineage).mockResolvedValue({
            informationRequestId: "request-a",
            successors: [{
                id: "lineage-a",
                lineageKind: InformationRequestLineageKind.RECURRENCE,
                sourceRequestId: "request-a",
                successorRequestId: "request-b",
                recurrenceId: "recurrence-a",
                recurrenceSequence: 1,
                createdAt: Date.parse("2026-09-10T08:00:00Z"),
            }],
            recurrence: {
                id: "recurrence-a",
                originRequestId: "request-a",
                intervalUnit: InformationRequestRecurrenceUnit.MONTH,
                intervalCount: 1,
                firstDueAt: Date.parse("2026-09-10T08:00:00Z"),
                createdAt: Date.parse("2026-09-01T08:00:00Z"),
            },
            nextOccurrenceDueAt: Date.parse("2026-09-26T08:00:00Z"),
        });
        renderPanel(false);

        expect(await screen.findByText("Recurring request 1")).toBeTruthy();
        expect(screen.getByText(/^Repeats every 1 month/)).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Create the next request"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Request a supplement"})).toBeNull();
    });

    it("states a refused lineage read with Try again, withholds the schedule and refresh controls, and offers them once a retry loads", async () => {
        vi.mocked(authoring.getInformationRequestLineage)
            .mockRejectedValueOnce({errorMessage: "Lineage access refused"})
            .mockResolvedValueOnce({informationRequestId: "request-a", successors: []});
        renderPanel();

        expect(await screen.findByText("Lineage access refused")).toBeTruthy();
        expect(document.getElementById("information-request-recurrence-form")).toBeNull();
        expect(document.getElementById("information-request-refresh-rules")).toBeNull();
        expect(screen.getByRole("button", {name: "Request a supplement"})).toBeTruthy();

        fireEvent.click(screen.getByRole("button", {name: "Try again"}));

        expect(await screen.findByLabelText("Repeat every")).toBeTruthy();
        expect(document.getElementById("information-request-refresh-rules")).not.toBeNull();
        expect(screen.queryByText("Lineage access refused")).toBeNull();
    });

    it("shows a loading state rather than an empty history while the lineage is read", async () => {
        let answer: (lineage: { informationRequestId: string; successors: never[] }) => void = () => undefined;
        vi.mocked(authoring.getInformationRequestLineage).mockImplementation(() => new Promise(resolve => {
            answer = resolve;
        }));
        renderPanel();

        expect(document.getElementById("information-request-follow-up-loading")).not.toBeNull();
        expect(document.getElementById("information-request-recurrence-form")).toBeNull();
        await act(async () => answer({informationRequestId: "request-a", successors: []}));
        expect(document.getElementById("information-request-follow-up-loading")).toBeNull();
        expect(document.getElementById("information-request-recurrence-form")).not.toBeNull();
    });

    it("offers no schedule while new work is unavailable", async () =>
    {
        vi.mocked(authoring.getInformationRequestLineage).mockResolvedValue({
            informationRequestId: "request-a",
            successors: []
        });
        renderPanel(false);

        await waitFor(() => expect(authoring.getInformationRequestLineage).toHaveBeenCalled());
        expect(document.getElementById("information-request-recurrence-form")).toBeNull();
    });

    it("lists the request's refresh rules and creates a refresh from one", async () => {
        vi.mocked(authoring.createInformationRequestRefresh).mockResolvedValue({} as never);
        vi.mocked(authoring.getInformationRequestLineage).mockResolvedValue({
            informationRequestId: "request-a",
            successors: [],
            refreshRules: [{
                id: "rule-a",
                informationRequestId: "request-a",
                requirementKey: "supporting-record",
                leadDays: 30,
                createdAt: Date.parse("2026-09-12T08:00:00Z"),
                refreshDueOn: "2026-10-09",
                refreshDue: true,
            }],
        });
        renderPanel();

        expect(await screen.findByText("Provide the supporting record")).toBeTruthy();
        expect(screen.getByText("A new request is created 30 days before its evidence expires.")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Refresh Provide the supporting record now"}));

        await waitFor(() => expect(authoring.createInformationRequestRefresh)
            .toHaveBeenCalledWith("request-a", "rule-a", expect.any(String)));
        expect(screen.queryByRole("option", {name: "Provide the supporting record"})).toBeNull();
    });

    it("defines a refresh rule only for a requested document whose expiry date is captured", async () => {
        vi.mocked(authoring.defineInformationRequestRefreshRule).mockResolvedValue({} as never);
        vi.mocked(authoring.getInformationRequestLineage).mockResolvedValue({
            informationRequestId: "request-a",
            successors: []
        });
        renderPanel();

        const requirement = await screen.findByLabelText("Document to refresh");
        expect(screen.getByRole("option", {name: "Provide the supporting record"})).toBeTruthy();
        expect(screen.getByRole("option", {name: "Provide the dated statement"})).toBeTruthy();
        expect(screen.queryByRole("option", {name: "Provide the cover note"})).toBeNull();
        fireEvent.change(requirement, {target: {value: "supporting-record"}});
        fireEvent.change(screen.getByLabelText("Days before expiry"), {target: {value: "14"}});
        fireEvent.click(screen.getByRole("button", {name: "Add refresh rule"}));

        await waitFor(() => expect(authoring.defineInformationRequestRefreshRule).toHaveBeenCalledWith(
            "request-a",
            {requirementKey: "supporting-record", leadDays: 14},
            "\"request-7\"",
            expect.any(String),
        ));
    });

    it("keeps refresh rules readable but offers no refresh while new work is unavailable", async () => {
        vi.mocked(authoring.getInformationRequestLineage).mockResolvedValue({
            informationRequestId: "request-a",
            successors: [],
            refreshRules: [{
                id: "rule-a",
                informationRequestId: "request-a",
                requirementKey: "supporting-record",
                leadDays: 30,
                createdAt: Date.parse("2026-09-12T08:00:00Z"),
            }],
        });
        renderPanel(false);

        expect(await screen.findByText("Provide the supporting record")).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Refresh Provide the supporting record now"})).toBeNull();
        expect(document.getElementById("information-request-refresh-rule-form")).toBeNull();
    });

    it.each([InformationRequestState.CANCELLED, InformationRequestState.SUPERSEDED])(
        "keeps a %s request's follow-up history readable but offers no new follow-up",
        async state => {
            lineageWithRefreshRule();
            renderPanel(true, state);

            expect(await screen.findByText("Provide the supporting record")).toBeTruthy();
            expect(screen.queryByRole("button", {name: "Refresh Provide the supporting record now"})).toBeNull();
            expect(document.getElementById("information-request-refresh-rule-form")).toBeNull();
            expect(document.getElementById("information-request-recurrence-schedule")).toBeNull();
            expect(screen.queryByRole("button", {name: "Request a supplement"})).toBeNull();
        },
    );

    it("keeps a closed request's follow-up history readable but offers no follow-up the server refuses after its Exchange ends", async () => {
        lineageWithRefreshRule();
        renderPanel(true, InformationRequestState.CLOSED, retainedRecordAuthorActions);

        expect(await screen.findByText("Provide the supporting record")).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Refresh Provide the supporting record now"})).toBeNull();
        expect(document.getElementById("information-request-refresh-rule-form")).toBeNull();
        expect(document.getElementById("information-request-recurrence-form")).toBeNull();
        expect(screen.queryByRole("button", {name: "Request a supplement"})).toBeNull();
        expect(screen.queryByLabelText("Reason for the supplement")).toBeNull();
    });

    it("schedules a draft's recurrence and refresh rules but offers no follow-up before the draft is issued", async () => {
        lineageWithRefreshRule();
        renderPanel(true, InformationRequestState.DRAFT);

        fireEvent.change(await screen.findByLabelText("Repeat every"), {target: {value: "2"}});
        fireEvent.change(screen.getByLabelText("Unit"), {target: {value: InformationRequestRecurrenceUnit.WEEK}});
        fireEvent.change(screen.getByLabelText("First due"), {target: {value: "2026-11-02T09:00"}});
        fireEvent.click(screen.getByRole("button", {name: "Schedule"}));

        await waitFor(() => expect(authoring.defineInformationRequestRecurrence).toHaveBeenCalledWith(
            "request-a",
            {
                intervalUnit: InformationRequestRecurrenceUnit.WEEK,
                intervalCount: 2,
                firstDueAt: new Date("2026-11-02T09:00").toISOString(),
            },
            "\"request-7\"",
            expect.any(String),
        ));
        expect(screen.getByLabelText("Document to refresh")).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Refresh Provide the supporting record now"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Request a supplement"})).toBeNull();
    });

    it("shows a draft's recurrence without offering the next request before the draft is issued", async () => {
        vi.mocked(authoring.getInformationRequestLineage).mockResolvedValue({
            informationRequestId: "request-a",
            successors: [],
            recurrence: {
                id: "recurrence-a",
                originRequestId: "request-a",
                intervalUnit: InformationRequestRecurrenceUnit.MONTH,
                intervalCount: 1,
                firstDueAt: Date.parse("2026-09-10T08:00:00Z"),
                createdAt: Date.parse("2026-09-01T08:00:00Z"),
            },
            nextOccurrenceDueAt: Date.parse("2026-09-26T08:00:00Z"),
        });
        renderPanel(true, InformationRequestState.DRAFT);

        expect(await screen.findByText(/^Repeats every 1 month/)).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Create the next request"})).toBeNull();
        expect(document.getElementById("information-request-recurrence-form")).toBeNull();
    });

    it("offers a supplement and a refresh from an expired request but no new schedule or refresh rule", async () => {
        lineageWithRefreshRule();
        renderPanel(true, InformationRequestState.EXPIRED);

        expect(await screen.findByRole("button", {name: "Refresh Provide the supporting record now"})).toBeTruthy();
        expect(screen.getByRole("button", {name: "Request a supplement"})).toBeTruthy();
        expect(document.getElementById("information-request-refresh-rule-form")).toBeNull();
        expect(document.getElementById("information-request-recurrence-schedule")).toBeNull();
    });

    it("offers the supplement reasons as a fixed list and sends the chosen reason code", async () => {
        vi.mocked(authoring.getInformationRequestLineage).mockResolvedValue({
            informationRequestId: "request-a",
            successors: []
        });
        renderPanel();

        const reason = await screen.findByRole("combobox", {name: "Reason for the supplement"});
        expect(within(reason).getAllByRole("option").map(option => option.textContent)).toEqual([
            "Choose a reason",
            "More information is needed",
            "A document is missing or incomplete",
            "A document has expired or is about to",
            "An answer needs clarifying",
        ]);
        const submit = screen.getByRole("button", {name: "Request a supplement"}) as HTMLButtonElement;
        expect(submit.disabled).toBe(true);
        fireEvent.change(reason, {target: {value: "document-missing"}});
        fireEvent.click(submit);

        await waitFor(() => expect(authoring.requestInformationRequestSupplement)
            .toHaveBeenCalledWith("request-a", "document-missing", "\"request-7\"", expect.any(String)));
        await waitFor(() => expect((reason as HTMLSelectElement).value).toBe(""));
        expect(submit.disabled).toBe(true);
    });

    it("announces a refused supplement and keeps its reason", async () => {
        vi.mocked(authoring.getInformationRequestLineage).mockResolvedValue({
            informationRequestId: "request-a",
            successors: []
        });
        vi.mocked(authoring.requestInformationRequestSupplement).mockRejectedValueOnce({errorMessage: "Supplement refused"});
        renderPanel();
        fireEvent.change(await screen.findByRole("combobox", {name: "Reason for the supplement"}), {target: {value: "answer-unclear"}});
        fireEvent.click(screen.getByRole("button", {name: "Request a supplement"}));

        await screen.findByText("Supplement refused");
        expect(screen.getByRole("alert").textContent).toContain("Supplement refused");
        expect((screen.getByRole("combobox", {name: "Reason for the supplement"}) as HTMLSelectElement).value).toBe("answer-unclear");
    });

    it("states an empty follow-up history when no new request can be created", async () => {
        vi.mocked(authoring.getInformationRequestLineage).mockResolvedValue({
            informationRequestId: "request-a",
            successors: []
        });
        renderPanel(false, InformationRequestState.CANCELLED);
        expect(await screen.findByText("No earlier or follow-up requests are recorded.")).toBeTruthy();
    });
});
