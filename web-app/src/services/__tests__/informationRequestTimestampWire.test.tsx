/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import apiClient, {setApiClientAuthToken} from "../apiClient.ts";
import {startInformationRequestClock} from "../informationRequestAdministrationService.ts";
import {
    promoteInformationRequestAcceptedFact,
    recordInformationRequestBusinessDecision,
} from "../informationRequestOutcomeService.ts";
import {assignInformationRequestReviewer} from "../informationRequestReviewService.ts";
import {jsonAnswer, LocalApiServer, startLocalApiServer} from "../testing/localApiServer.ts";
import {
    InformationRequestBusinessDecisionKind,
    InformationRequestClockUrgency,
    InformationRequestPartyDto,
    InformationRequestReviewAggregation,
    InformationRequestReviewStageStandingDto,
    InformationRequestReviewStageState,
    InformationRequestReviewTieResolution,
    InformationRequestShareRoleKey,
} from "../../app/models/models.tsx";
import PromoteFactDialog from "../../app/information-requests/authoring/promote-fact-dialog/PromoteFactDialog.tsx";
import RecordDecisionDialog
    from "../../app/information-requests/authoring/record-decision-dialog/RecordDecisionDialog.tsx";
import AssignReviewerDialog
    from "../../app/information-requests/review/assign-reviewer-dialog/AssignReviewerDialog.tsx";

const requestPath = "/information-requests/request-a";
const reviewerOptions = {expectedETag: "\"review:2\"", idempotencyKey: "assign-a"};

const stage: InformationRequestReviewStageStandingDto = {
    stageKey: "content",
    title: "Content review",
    position: 1,
    aggregation: InformationRequestReviewAggregation.ALL,
    minimumReviewerCount: 1,
    tieResolution: InformationRequestReviewTieResolution.MOST_SEVERE_OUTCOME,
    overridePermitted: false,
    excludesResponseParties: true,
    excludesPriorReviewers: false,
    state: InformationRequestReviewStageState.OPEN,
    items: [],
};

const reviewer: InformationRequestPartyDto = {
    id: "party-b",
    informationRequestId: "request-a",
    roleKey: InformationRequestShareRoleKey.REVIEWER,
    active: true,
    assignedAt: Date.parse("2026-09-26T08:00:00Z"),
    partyRevision: 1,
    partyETag: "\"party:1\"",
    label: "Second reviewer",
};

let server: LocalApiServer;

const choose = (container: HTMLElement, label: string, option: string) => {
    const select = within(container).getByLabelText(label) as HTMLSelectElement;
    fireEvent.change(select, {target: {value: Array.from(select.options).find(candidate => candidate.textContent === option)?.value ?? ""}});
};

const bodySentTo = async (path: string): Promise<Record<string, unknown>> => {
    await waitFor(() => expect(server.received.some(request => request.method === "POST" && request.path === path)).toBe(true));
    const sent = server.received.filter(request => request.method === "POST" && request.path === path);
    expect(sent).toHaveLength(1);
    return JSON.parse(sent[0].body) as Record<string, unknown>;
};

const localInstant = (value: string): number => new Date(value).getTime();

const assignWith = (due: string) => {
    render(<AssignReviewerDialog stages={[stage]}
                                 parties={[reviewer]}
                                 busy={false}
                                 onConfirm={request => void assignInformationRequestReviewer("request-a", "review-a", request, reviewerOptions)}
                                 onDismiss={() => undefined}/>);
    const dialog = screen.getByRole("dialog", {name: "Assign a reviewer"});
    choose(dialog, "Reviewer", "Second reviewer");
    if (due) fireEvent.change(within(dialog).getByLabelText("Due"), {target: {value: due}});
    fireEvent.click(within(dialog).getByRole("button", {name: "Assign"}));
    return bodySentTo(`${requestPath}/reviews/review-a/assignments`);
};

const promoteWith = (dates: { validFrom?: string; validTo?: string; expiresAt?: string }) => {
    render(<PromoteFactDialog label={"Record identifier"}
                              evidenceChoices={[]}
                              promotedPurposes={[]}
                              busy={false}
                              onConfirm={terms => void promoteInformationRequestAcceptedFact(
                                  "request-a",
                                  {packageId: "package-a", submissionItemId: "item-a", ...terms},
                                  "promote-a",
                              )}
                              onDismiss={() => undefined}/>);
    const dialog = screen.getByRole("dialog", {name: "Promote Record identifier"});
    fireEvent.change(within(dialog).getByLabelText(/Purpose/), {target: {value: "record-review"}});
    fireEvent.change(within(dialog).getByLabelText(/Reuse policy basis/), {target: {value: "retained-confirmation"}});
    if (dates.validFrom) fireEvent.change(within(dialog).getByLabelText("Valid from"), {target: {value: dates.validFrom}});
    if (dates.validTo) fireEvent.change(within(dialog).getByLabelText("Valid until"), {target: {value: dates.validTo}});
    if (dates.expiresAt) fireEvent.change(within(dialog).getByLabelText("Stop offering it after"), {target: {value: dates.expiresAt}});
    fireEvent.click(within(dialog).getByRole("button", {name: "Promote"}));
    return bodySentTo(`${requestPath}/accepted-facts`);
};

describe("dated Information Request commands on the wire", () => {
    beforeAll(async () => {
        vi.stubGlobal("ResizeObserver", class {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
        server = await startLocalApiServer(() => jsonAnswer(200, {}));
        apiClient.defaults.adapter = "xhr";
        apiClient.defaults.baseURL = server.origin;
        setApiClientAuthToken(null);
    });

    beforeEach(() => {
        server.received.length = 0;
    });

    afterEach(() => cleanup());

    afterAll(async () => {
        vi.unstubAllGlobals();
        await server.close();
    });

    it("sends a reviewer due time as epoch milliseconds", async () => {
        const body = await assignWith("2099-10-06T09:30");

        expect(body).toEqual({
            stageKey: "content",
            reviewerPartyId: "party-b",
            dueAt: localInstant("2099-10-06T09:30")
        });
    });

    it("leaves the due time out of an undated reviewer assignment", async () => {
        const body = await assignWith("");

        expect(body).toEqual({stageKey: "content", reviewerPartyId: "party-b"});
    });

    it("sends an accepted fact's validity and expiry times as epoch milliseconds", async () => {
        const body = await promoteWith({
            validFrom: "2026-09-01T08:00",
            validTo: "2099-09-01T08:00",
            expiresAt: "2099-10-01T08:00",
        });

        expect(body).toEqual({
            packageId: "package-a",
            submissionItemId: "item-a",
            purposeKey: "record-review",
            policyBasisKey: "retained-confirmation",
            visibility: "REQUESTING_SIDE",
            validFrom: localInstant("2026-09-01T08:00"),
            validTo: localInstant("2099-09-01T08:00"),
            expiresAt: localInstant("2099-10-01T08:00"),
        });
    });

    it("leaves every date out of an accepted fact promoted without dates", async () => {
        const body = await promoteWith({});

        expect(Object.keys(body).sort()).toEqual(["packageId", "policyBasisKey", "purposeKey", "submissionItemId", "visibility"]);
    });

    it("sends a business decision's decided time as epoch milliseconds", async () => {
        render(<RecordDecisionDialog kind={InformationRequestBusinessDecisionKind.ORIGINAL}
                                     busy={false}
                                     onConfirm={request => void recordInformationRequestBusinessDecision("request-a", request, "decide-a")}
                                     onDismiss={() => undefined}/>);
        const dialog = screen.getByRole("dialog", {name: "Record a business decision"});
        fireEvent.change(within(dialog).getByLabelText(/Process/), {target: {value: "record-review"}});
        fireEvent.change(within(dialog).getByLabelText(/Outcome/), {target: {value: "completed"}});
        fireEvent.change(within(dialog).getByLabelText(/Decided/), {target: {value: "2026-09-01T08:00"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Record decision"}));

        expect(await bodySentTo(`${requestPath}/business-decisions`)).toEqual({
            owningProcessKey: "record-review",
            outcomeCode: "completed",
            kind: InformationRequestBusinessDecisionKind.ORIGINAL,
            decidedAt: localInstant("2026-09-01T08:00"),
        });
    });

    it("sends an explicit clock received time as epoch milliseconds and leaves an unstated one out", async () => {
        const receivedAt = Date.UTC(2026, 8, 1, 8, 0);
        await startInformationRequestClock(
            "request-a",
            {
                clockKey: "response",
                policyVersionId: "policy-a",
                urgency: InformationRequestClockUrgency.STANDARD,
                receivedAt
            },
            "clock-a",
        );
        await startInformationRequestClock(
            "request-b",
            {clockKey: "response", policyVersionId: "policy-a", urgency: InformationRequestClockUrgency.STANDARD},
            "clock-b",
        );

        expect(await bodySentTo(`${requestPath}/clocks`)).toEqual({
            clockKey: "response",
            policyVersionId: "policy-a",
            urgency: "STANDARD",
            receivedAt,
        });
        expect(await bodySentTo("/information-requests/request-b/clocks")).toEqual({
            clockKey: "response",
            policyVersionId: "policy-a",
            urgency: "STANDARD",
        });
    });
});
