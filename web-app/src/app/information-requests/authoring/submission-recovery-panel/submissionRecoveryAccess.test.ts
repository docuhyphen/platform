import {describe, expect, it} from "vitest";
import {
    InformationRequestAuthorActionsDto,
    InformationRequestDto,
    InformationRequestExecutionStandingKind,
    InformationRequestOwnerType,
    InformationRequestState,
} from "../../../models/models.tsx";
import {everyAuthorAction} from "../testing/authorActions.ts";
import {maySubmissionsBeRecovered} from "./submissionRecoveryAccess.ts";

const request = (state: InformationRequestState): InformationRequestDto => ({
    id: "request-a",
    exchangeId: "exchange-a",
    ownerType: InformationRequestOwnerType.ORGANIZATION,
    state,
    gatesExchangeClosure: false,
    aggregateRevision: 1,
    createdAt: Date.parse("2026-09-01T08:00:00Z"),
    updatedAt: Date.parse("2026-09-01T08:00:00Z"),
    requestETag: "\"request-1\"",
    executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
    conditionEvaluations: [],
});

const workspace = (
    state: InformationRequestState,
    kind: InformationRequestExecutionStandingKind,
    authorActions: InformationRequestAuthorActionsDto | null = everyAuthorAction,
) => ({request: request(state), executionStanding: {kind}, authorActions});

describe("maySubmissionsBeRecovered", () => {
    it("offers recovery to a party manager while an unfinished request can still change", () => {
        expect(maySubmissionsBeRecovered(workspace(InformationRequestState.IN_PROGRESS, InformationRequestExecutionStandingKind.ACTIVE))).toBe(true);
        expect(maySubmissionsBeRecovered(workspace(InformationRequestState.ISSUED, InformationRequestExecutionStandingKind.CONTINUING_AFTER_LAPSE))).toBe(true);
    });

    it("offers no recovery without party management, on a finished request, or while changes are paused or stopped", () => {
        expect(maySubmissionsBeRecovered(workspace(InformationRequestState.IN_PROGRESS, InformationRequestExecutionStandingKind.ACTIVE, {...everyAuthorAction, manageParties: false}))).toBe(false);
        expect(maySubmissionsBeRecovered(workspace(InformationRequestState.IN_PROGRESS, InformationRequestExecutionStandingKind.ACTIVE, null))).toBe(false);
        expect(maySubmissionsBeRecovered(workspace(InformationRequestState.CLOSED, InformationRequestExecutionStandingKind.ACTIVE))).toBe(false);
        expect(maySubmissionsBeRecovered(workspace(InformationRequestState.DRAFT, InformationRequestExecutionStandingKind.ACTIVE))).toBe(false);
        expect(maySubmissionsBeRecovered(workspace(InformationRequestState.IN_PROGRESS, InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED))).toBe(false);
        expect(maySubmissionsBeRecovered(workspace(InformationRequestState.IN_PROGRESS, InformationRequestExecutionStandingKind.EXECUTION_GRANT_REVOKED))).toBe(false);
        expect(maySubmissionsBeRecovered(workspace(InformationRequestState.IN_PROGRESS, InformationRequestExecutionStandingKind.NEW_WORK_UNAVAILABLE))).toBe(false);
    });
});
