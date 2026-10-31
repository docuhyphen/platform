/** @vitest-environment jsdom */
import {cleanup, render, screen} from "@testing-library/react";
import {MemoryRouter, Route, Routes} from "react-router-dom";
import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import apiClient, {setApiClientAuthToken} from "../apiClient.ts";
import {getInformationRequestAccessLinks} from "../informationRequestAuthoringService.ts";
import {getInformationRequestOperations} from "../informationRequestOperationsService.ts";
import {getInformationRequestAcceptedFactOffers} from "../informationRequestReuseService.ts";
import {jsonAnswer, LocalAnswer, LocalApiServer, startLocalApiServer} from "../testing/localApiServer.ts";
import {usePlanFeature} from "../../hooks/subscription/usePlanFeature.ts";
import {
    FieldValueType,
    InformationRequestAcceptedFactConfidence,
    InformationRequestAcceptedFactFreshness,
    InformationRequestAcceptedFactOfferDto,
    InformationRequestAccessLinkDto,
    InformationRequestAccessLinkStatus,
    InformationRequestExecutionStandingKind,
    InformationRequestOperationsPageDto,
    InformationRequestPartyDto,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestReviewAggregation,
    InformationRequestReviewAssignmentState,
    InformationRequestReviewDecisionKind,
    InformationRequestReviewDto,
    InformationRequestReviewItemStanding,
    InformationRequestReviewKind,
    InformationRequestReviewOutcome,
    InformationRequestReviewStageOrdering,
    InformationRequestReviewStageState,
    InformationRequestReviewState,
    InformationRequestReviewTieResolution,
    InformationRequestShareRoleKey,
    InformationRequestSlaStatus,
    InformationRequestState,
} from "../../app/models/models.tsx";
import InformationRequestReviewWorkspace
    from "../../app/information-requests/review/review-workspace/InformationRequestReviewWorkspace.tsx";
import PartyRow from "../../app/information-requests/authoring/party-row/PartyRow.tsx";
import ReusableAnswerOffer
    from "../../app/information-requests/reusable-answer/reusable-answer-offer/ReusableAnswerOffer.tsx";
import {operationsCsv} from "../../app/information-requests/operations/operationsCsv.ts";
import {formatInformationRequestTime} from "../../app/information-requests/shared/informationRequestFormatting.ts";

vi.mock("../../hooks/subscription/usePlanFeature.ts", () => ({usePlanFeature: vi.fn()}));
vi.mock("../stepUpBroker.ts", () => ({requestStepUp: vi.fn()}));

const earlierDecision = Date.parse("2026-09-26T09:00:00Z");
const laterDecision = Date.parse("2026-09-26T10:30:00Z");
const expiredLink = Date.parse("2020-01-01T08:00:00Z");
const nearestDue = Date.parse("2026-09-25T08:00:00Z");
const created = Date.parse("2026-09-20T08:00:00Z");

const review: InformationRequestReviewDto = {
    review: {
        id: "review-a",
        informationRequestId: "request-a",
        packageId: "package-a",
        packageNumber: 1,
        reviewNumber: 1,
        kind: InformationRequestReviewKind.INITIAL,
        state: InformationRequestReviewState.IN_REVIEW,
        openedAt: Date.parse("2026-09-26T08:00:00Z"),
        reviewETag: "\"review:3\"",
    },
    reviewStageOrdering: InformationRequestReviewStageOrdering.SEQUENTIAL,
    stages: [{
        stageKey: "review",
        title: "Review",
        position: 1,
        aggregation: InformationRequestReviewAggregation.ANY,
        minimumReviewerCount: 1,
        tieResolution: InformationRequestReviewTieResolution.MOST_SEVERE_OUTCOME,
        overridePermitted: false,
        excludesResponseParties: false,
        excludesPriorReviewers: false,
        state: InformationRequestReviewStageState.OPEN,
        items: [
            {submissionItemId: "item-a", standing: InformationRequestReviewItemStanding.DECIDED},
            {submissionItemId: "item-b", standing: InformationRequestReviewItemStanding.DECIDED},
        ],
    }],
    items: ["item-a", "item-b"].map((submissionItemId, index) => ({
        submissionItemId,
        requirementId: `requirement-${index}`,
        requirementKey: index === 0 ? "recorded-summary" : "supporting-note",
        requirementType: InformationRequestRequirementType.FIELD,
        occurrencePath: "root",
        reviewed: true,
        contentVisible: true,
        waiverPermitted: false,
        disposition: InformationRequestResponseDisposition.PROVIDED,
        fieldValue: "Synthetic value",
        evidence: [],
    })),
    assignments: [{
        id: "assignment-a",
        stageKey: "review",
        reviewerPartyId: "party-a",
        state: InformationRequestReviewAssignmentState.ACTIVE,
        assignedAt: Date.parse("2026-09-26T08:05:00Z"),
        callerIsReviewer: true,
        delegationCandidates: [],
    }],
    decisions: [
        {
            id: "decision-b",
            stageKey: "review",
            submissionItemId: "item-b",
            kind: InformationRequestReviewDecisionKind.REVIEWER,
            outcome: InformationRequestReviewOutcome.CHANGES_REQUIRED,
            decidedAt: laterDecision,
            decidedByCaller: true,
        },
        {
            id: "decision-a",
            stageKey: "review",
            submissionItemId: "item-a",
            kind: InformationRequestReviewDecisionKind.REVIEWER,
            outcome: InformationRequestReviewOutcome.SATISFIED,
            decidedAt: earlierDecision,
            decidedByCaller: true,
        },
    ],
    findings: [],
    comments: [],
    remediations: [],
    worksheets: [{assignmentId: "assignment-a", draftETag: "\"draft:1\"", draftRevision: 1, entries: []}],
    canManage: false,
    earlierFindings: [],
    earlierRemediations: [],
    canReconsider: false,
    canComment: true,
    itemsAwaitingStepUp: 0,
};

const accessLinks: InformationRequestAccessLinkDto[] = [{
    shareLinkId: "link-a",
    status: InformationRequestAccessLinkStatus.ACTIVE,
    expiresAt: expiredLink,
    maxUses: 25,
    rotationCount: 0,
    partyId: "party-c",
    createdAt: Date.parse("2019-12-01T08:00:00Z"),
}];

const operations: InformationRequestOperationsPageDto = {
    items: [
        {
            requestId: "request-a",
            exchangeId: "exchange-a",
            title: "Periodic records request",
            assignees: [],
            templateVersionId: "version-a",
            state: InformationRequestState.IN_PROGRESS,
            gatesExchangeClosure: true,
            createdAt: created,
            ageSeconds: 3600,
            slaStatus: InformationRequestSlaStatus.OVERDUE,
            nearestDueAt: nearestDue,
            clockCount: 1,
            reminderCount: 0,
            noticeCounts: {},
            exceptionCounts: {},
            executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
        },
        {
            requestId: "request-b",
            exchangeId: "exchange-a",
            title: "Supplemental records request",
            assignees: [],
            templateVersionId: "version-a",
            state: InformationRequestState.ISSUED,
            gatesExchangeClosure: true,
            createdAt: created,
            issuedAt: created,
            firstViewedAt: null,
            startedAt: null,
            ageSeconds: 3600,
            slaStatus: InformationRequestSlaStatus.NO_CLOCK,
            nearestDueAt: null,
            clockCount: 0,
            reminderCount: 0,
            noticeCounts: {},
            exceptionCounts: {},
            executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
        },
    ],
    total: 2,
    limit: 25,
    offset: 0,
};

const factCutoff = Date.parse("2027-03-01T08:00:00Z");

const offerEnding = (requirementId: string, validTo: number | null, expiresAt: number | null): InformationRequestAcceptedFactOfferDto => ({
    requirementId,
    requirementKey: requirementId,
    reconfirmationRequired: false,
    fact: {
        id: `fact-${requirementId}`,
        purposeKey: "profile.reuse",
        policyBasisKey: "policy.reuse",
        valueType: FieldValueType.SHORT_TEXT,
        value: "Recorded answer",
        confidence: InformationRequestAcceptedFactConfidence.REVIEWED,
        validFrom: Date.parse("2026-09-01T08:00:00Z"),
        validTo,
        expiresAt,
        freshness: InformationRequestAcceptedFactFreshness.CURRENT,
    },
});

const offers: InformationRequestAcceptedFactOfferDto[] = [
    offerEnding("unbounded-note", null, null),
    offerEnding("cutoff-note", null, factCutoff),
    offerEnding("epoch-note", 0, null),
];

const contributor: InformationRequestPartyDto = {
    id: "party-c",
    informationRequestId: "request-a",
    roleKey: InformationRequestShareRoleKey.CONTRIBUTOR,
    active: true,
    principalId: "participant-c",
    principalKind: "PARTICIPANT",
    assignedAt: Date.parse("2019-12-01T08:00:00Z"),
    partyRevision: 1,
    partyETag: "\"party-c-1\"",
    label: "Counterparty desk",
};

const EVERY_PARTY_ACTION = {change: true, reduceAccess: true, issueLinks: true};

const notFound = jsonAnswer(404, {reasonCode: "INFORMATION_REQUEST_NOT_FOUND", errorMessage: "Not found"});

const answers: Record<string, LocalAnswer> = {
    "GET /information-requests/request-a/reviews/review-a": jsonAnswer(200, review),
    "GET /information-requests/request-a/parties": jsonAnswer(200, []),
    "GET /information-requests/request-a/access-links": jsonAnswer(200, accessLinks),
    "GET /information-request-operations": jsonAnswer(200, operations),
    "GET /information-requests/request-a/accepted-fact-offers": jsonAnswer(200, offers),
};

const serverBody = (key: string): string => answers[key].body;

let server: LocalApiServer;

const shownTime = (value: number): string => formatInformationRequestTime(value).replace(/\s+/g, " ");

describe("Information Request response times read over HTTP as the server sends them", () => {
    beforeAll(async () => {
        vi.stubGlobal("ResizeObserver", class {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
        server = await startLocalApiServer((method, path) => answers[`${method} ${path.split("?")[0]}`] ?? notFound);
        apiClient.defaults.adapter = "xhr";
        apiClient.defaults.baseURL = server.origin;
        setApiClientAuthToken(null);
    });

    beforeEach(() => {
        vi.mocked(usePlanFeature).mockReturnValue({
            isKnown: true,
            isIncluded: true,
            isEnforced: true,
            isDiscoverable: true,
            isAvailable: true,
            upgradePlanCode: null,
        });
    });

    afterEach(() => cleanup());

    afterAll(async () => {
        vi.unstubAllGlobals();
        await server.close();
    });

    it("sends every time as a JSON number of epoch milliseconds", () => {
        expect(serverBody("GET /information-requests/request-a/reviews/review-a")).toContain(`"decidedAt":${laterDecision}`);
        expect(serverBody("GET /information-requests/request-a/access-links")).toContain(`"expiresAt":${expiredLink}`);
        expect(serverBody("GET /information-request-operations")).toContain(`"nearestDueAt":${nearestDue}`);
    });

    it("sends an unset time as an explicit null, as the server's JSON configuration writes it", () => {
        expect(serverBody("GET /information-request-operations")).toContain(`"nearestDueAt":null`);
        expect(serverBody("GET /information-requests/request-a/accepted-fact-offers")).toContain(`"validTo":null,"expiresAt":null`);
    });

    it("shows a review with two decisions on the review page, earliest first", async () => {
        render(
            <MemoryRouter initialEntries={["/information-requests/request-a/reviews/review-a"]}>
                <Routes>
                    <Route path={"/information-requests/:requestId/reviews/:reviewId"}
                           element={<InformationRequestReviewWorkspace/>}/>
                </Routes>
            </MemoryRouter>,
        );

        const history = await screen.findByRole("region", {name: "Decision history"});
        const listed = Array.from(history.querySelectorAll("li")).map(item => item.id);
        expect(listed).toEqual([
            "information-request-review-history-decision-a",
            "information-request-review-history-decision-b",
        ]);
        expect(document.getElementById("information-request-review-history-decision-a-decided")?.textContent?.replace(/\s+/g, " "))
            .toBe(` (Review, ${shownTime(earlierDecision)})`);
        expect(document.getElementById("information-request-review-history-decision-b-decided")?.textContent?.replace(/\s+/g, " "))
            .toBe(` (Review, ${shownTime(laterDecision)})`);
    });

    it("shows an active access link whose expiry has passed as expired and offers to revoke it", async () => {
        const links = await getInformationRequestAccessLinks("request-a");
        render(
            <ul>
                <PartyRow party={contributor}
                          links={links}
                          busy={false}
                          permissions={EVERY_PARTY_ACTION}
                          onIssueLink={vi.fn()}
                          onResendLink={vi.fn()}
                          onRevokeLink={vi.fn()}
                          onReassign={vi.fn()}
                          onRemove={vi.fn()}/>
            </ul>,
        );

        expect(screen.getByText(`Contributor. Access link expired on ${shownTime(expiredLink)}`)).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Resend link to Counterparty desk"})).toBeNull();
        expect(screen.getByRole("button", {name: "Revoke link for Counterparty desk"})).toBeTruthy();
    });

    it("exports the operations queue with machine-readable due and created times", async () => {
        const page = await getInformationRequestOperations({
            states: [],
            exceptions: [],
            exceptionsOnly: false,
            limit: 25,
            offset: 0,
        });

        const columns = operationsCsv(page.items).split("\r\n")[1].split(",");
        expect(columns[4]).toBe("2026-09-25T08:00:00.000Z");
        expect(columns[5]).toBe("2026-09-20T08:00:00.000Z");
    });

    it("exports a request without a deadline with an empty Due cell", async () => {
        const page = await getInformationRequestOperations({
            states: [],
            exceptions: [],
            exceptionsOnly: false,
            limit: 25,
            offset: 0,
        });

        const columns = operationsCsv(page.items).split("\r\n")[2].split(",");
        expect(columns[0]).toBe("request-b");
        expect(columns[3]).toBe("No clock");
        expect(columns[4]).toBe("");
        expect(columns[5]).toBe("2026-09-20T08:00:00.000Z");
    });

    it("shows a reusable answer's end only where the server sends one, and epoch zero as an end", async () => {
        const read = await getInformationRequestAcceptedFactOffers("request-a");
        render(
            <>
                {read.map(offer => (
                    <ReusableAnswerOffer key={offer.requirementId}
                                         elementId={offer.requirementId}
                                         requestId={"request-a"}
                                         requirementId={offer.requirementId}
                                         offer={offer}
                                         responseETag={"\"responses-1\""}
                                         busy={false}
                                         recertify={vi.fn()}
                                         onApplied={vi.fn()}
                                         onResult={vi.fn()}
                                         onCommandStart={vi.fn()}
                                         onCommandFailure={vi.fn()}/>
                ))}
            </>,
        );

        const standing = (requirementId: string) =>
            document.getElementById(`information-request-reusable-answer-${requirementId}-OFFER-standing`)?.textContent;
        expect(standing("unbounded-note")).toBe("Accepted by a reviewer. No end date");
        expect(standing("cutoff-note")).toBe(`Accepted by a reviewer. Valid until ${formatInformationRequestTime(factCutoff)}`);
        expect(standing("epoch-note")).toBe(`Accepted by a reviewer. Valid until ${formatInformationRequestTime(0)}`);
    });
});
