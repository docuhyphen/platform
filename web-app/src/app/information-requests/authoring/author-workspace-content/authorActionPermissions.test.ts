import {describe, expect, it} from "vitest";
import {
    InformationRequestAuthorActionsDto,
    InformationRequestExecutionStandingKind,
    InformationRequestState,
} from "../../../models/models.tsx";
import {everyAuthorAction, noAuthorAction, retainedRecordAuthorActions} from "../testing/authorActions.ts";
import {AuthorActionPermissions, authorActionPermissions, NO_AUTHOR_ACTIONS} from "./authorActionPermissions.ts";

const permissionsUnder = (
    kind: InformationRequestExecutionStandingKind,
    state = InformationRequestState.IN_PROGRESS,
    actions: InformationRequestAuthorActionsDto | null | undefined = everyAuthorAction,
) => authorActionPermissions(state, {kind}, actions);

describe("authorActionPermissions", () => {
    it("allows every author action on an issued request while the request stands ACTIVE", () => {
        expect(permissionsUnder(InformationRequestExecutionStandingKind.ACTIVE)).toEqual({
            lifecycle: {issue: false, cancel: true, supersede: true},
            parties: {change: true, reduceAccess: true, issueLinks: true},
            clocks: {startOrResume: true, pauseOrExtend: true},
            completionGate: true,
            followUps: {schedule: true, create: true},
            outcomes: {promote: true, revoke: true, decide: true},
            preview: true,
        });
    });

    it("offers a new access link only while the server says links can be issued on this Exchange and changes are allowed", () => {
        expect(permissionsUnder(InformationRequestExecutionStandingKind.ACTIVE).parties.issueLinks).toBe(true);
        expect(permissionsUnder(InformationRequestExecutionStandingKind.ACTIVE, InformationRequestState.IN_PROGRESS, {
            ...everyAuthorAction,
            issueAccessLinks: false,
        }).parties.issueLinks).toBe(false);
        expect(permissionsUnder(InformationRequestExecutionStandingKind.ACTIVE, InformationRequestState.IN_PROGRESS, {
            ...everyAuthorAction,
            issueAccessLinks: undefined,
        }).parties.issueLinks).toBe(false);
        expect(permissionsUnder(InformationRequestExecutionStandingKind.EXECUTION_GRANT_REVOKED).parties.issueLinks).toBe(false);
        expect(permissionsUnder(InformationRequestExecutionStandingKind.ACTIVE, InformationRequestState.CLOSED).parties.issueLinks).toBe(false);
    });

    it("keeps changes but offers no follow-up while the request continues after a lapse", () => {
        expect(permissionsUnder(InformationRequestExecutionStandingKind.CONTINUING_AFTER_LAPSE)).toEqual({
            lifecycle: {issue: false, cancel: true, supersede: true},
            parties: {change: true, reduceAccess: true, issueLinks: true},
            clocks: {startOrResume: true, pauseOrExtend: true},
            completionGate: true,
            followUps: {schedule: false, create: false},
            outcomes: {promote: true, revoke: true, decide: true},
            preview: true,
        });
    });

    it("keeps owner recovery and access reductions after the grant is revoked", () => {
        expect(permissionsUnder(InformationRequestExecutionStandingKind.EXECUTION_GRANT_REVOKED)).toEqual({
            lifecycle: {issue: false, cancel: true, supersede: true},
            parties: {change: false, reduceAccess: true, issueLinks: false},
            clocks: {startOrResume: false, pauseOrExtend: true},
            completionGate: true,
            followUps: {schedule: false, create: false},
            outcomes: {promote: false, revoke: true, decide: false},
            preview: true,
        });
    });

    it.each([
        [InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED, false],
        [InformationRequestExecutionStandingKind.NEW_WORK_UNAVAILABLE, true],
    ])("keeps only access reductions, revocation, the preview and, unless suspended, cancellation while the request stands %s", (kind, cancels) => {
        expect(permissionsUnder(kind)).toEqual({
            lifecycle: {issue: false, cancel: cancels, supersede: false},
            parties: {change: false, reduceAccess: true, issueLinks: false},
            clocks: {startOrResume: false, pauseOrExtend: false},
            completionGate: false,
            followUps: {schedule: false, create: false},
            outcomes: {promote: false, revoke: true, decide: false},
            preview: true,
        });
    });

    it("lets the owner cancel a draft that may start no new work and stop it holding back the Exchange, but not supersede it or hold the Exchange again", () => {
        const holding = authorActionPermissions(
            InformationRequestState.DRAFT,
            {kind: InformationRequestExecutionStandingKind.NEW_WORK_UNAVAILABLE},
            everyAuthorAction,
            true,
        );
        const released = authorActionPermissions(
            InformationRequestState.DRAFT,
            {kind: InformationRequestExecutionStandingKind.NEW_WORK_UNAVAILABLE},
            everyAuthorAction,
            false,
        );

        expect(holding.lifecycle).toEqual({issue: false, cancel: true, supersede: false});
        expect(holding.completionGate).toBe(true);
        expect(released.completionGate).toBe(false);
    });

    it("withdraws nothing while the owner is operationally suspended", () => {
        const suspended = authorActionPermissions(
            InformationRequestState.DRAFT,
            {kind: InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED},
            everyAuthorAction,
            true,
        );

        expect(suspended.lifecycle.cancel).toBe(false);
        expect(suspended.completionGate).toBe(false);
    });

    it("issues or supersedes only a draft whose owner may start new work, and cancels it either way", () => {
        expect(permissionsUnder(InformationRequestExecutionStandingKind.ACTIVE, InformationRequestState.DRAFT).lifecycle)
            .toEqual({issue: true, cancel: true, supersede: true});
        expect(permissionsUnder(InformationRequestExecutionStandingKind.NEW_WORK_UNAVAILABLE, InformationRequestState.DRAFT).lifecycle)
            .toEqual({issue: false, cancel: true, supersede: false});
    });

    it("lets the owner prepare a draft, including its follow-up schedule, but start no follow-up or outcome before it is issued", () => {
        expect(permissionsUnder(InformationRequestExecutionStandingKind.ACTIVE, InformationRequestState.DRAFT)).toEqual({
            lifecycle: {issue: true, cancel: true, supersede: true},
            parties: {change: true, reduceAccess: true, issueLinks: true},
            clocks: {startOrResume: true, pauseOrExtend: true},
            completionGate: true,
            followUps: {schedule: true, create: false},
            outcomes: {promote: false, revoke: false, decide: false},
            preview: true,
        });
    });

    it.each([
        [InformationRequestExecutionStandingKind.NEW_WORK_UNAVAILABLE, true],
        [InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED, false],
    ])("keeps only access reductions, the preview and, unless suspended, cancellation on a draft whose standing is %s", (kind, cancels) => {
        expect(permissionsUnder(kind, InformationRequestState.DRAFT)).toEqual({
            lifecycle: {issue: false, cancel: cancels, supersede: false},
            parties: {change: false, reduceAccess: true, issueLinks: false},
            clocks: {startOrResume: false, pauseOrExtend: false},
            completionGate: false,
            followUps: {schedule: false, create: false},
            outcomes: {promote: false, revoke: false, decide: false},
            preview: true,
        });
    });

    it.each([
        InformationRequestState.CANCELLED,
        InformationRequestState.SUPERSEDED,
    ])("keeps only revocation and the preview once the request is %s", state => {
        expect(permissionsUnder(InformationRequestExecutionStandingKind.ACTIVE, state)).toEqual({
            lifecycle: {issue: false, cancel: false, supersede: false},
            parties: {change: false, reduceAccess: false, issueLinks: false},
            clocks: {startOrResume: false, pauseOrExtend: false},
            completionGate: false,
            followUps: {schedule: false, create: false},
            outcomes: {promote: false, revoke: true, decide: true},
            preview: true,
        });
    });

    it("keeps outcomes and follow-ups on a closed request but nothing that changes its work", () => {
        expect(permissionsUnder(InformationRequestExecutionStandingKind.ACTIVE, InformationRequestState.CLOSED)).toEqual({
            lifecycle: {issue: false, cancel: false, supersede: false},
            parties: {change: false, reduceAccess: false, issueLinks: false},
            clocks: {startOrResume: false, pauseOrExtend: false},
            completionGate: false,
            followUps: {schedule: true, create: true},
            outcomes: {promote: true, revoke: true, decide: true},
            preview: true,
        });
    });

    it("offers on a closed request whose Exchange ended only the fact revocation the server still permits", () => {
        expect(permissionsUnder(InformationRequestExecutionStandingKind.ACTIVE, InformationRequestState.CLOSED, retainedRecordAuthorActions))
            .toEqual({...NO_AUTHOR_ACTIONS, outcomes: {promote: false, revoke: true, decide: false}});
    });

    it("offers nothing on an open request the server lets the caller change in no way, whatever its standing", () => {
        expect(permissionsUnder(InformationRequestExecutionStandingKind.ACTIVE, InformationRequestState.IN_PROGRESS, noAuthorAction))
            .toEqual(NO_AUTHOR_ACTIONS);
    });

    it.each([null, undefined])("offers nothing when the workspace carries no author actions (%s)", actions => {
        expect(authorActionPermissions(InformationRequestState.IN_PROGRESS, {kind: InformationRequestExecutionStandingKind.ACTIVE}, actions))
            .toEqual(NO_AUTHOR_ACTIONS);
    });

    it.each([
        ["issue", InformationRequestState.DRAFT, (permissions: AuthorActionPermissions) => permissions.lifecycle.issue],
        ["cancel", InformationRequestState.IN_PROGRESS, (permissions: AuthorActionPermissions) => permissions.lifecycle.cancel],
        ["supersede", InformationRequestState.IN_PROGRESS, (permissions: AuthorActionPermissions) => permissions.lifecycle.supersede],
        ["supersede", InformationRequestState.DRAFT, (permissions: AuthorActionPermissions) => permissions.lifecycle.supersede],
        ["changeCompletionGate", InformationRequestState.IN_PROGRESS, (permissions: AuthorActionPermissions) => permissions.completionGate],
        ["manageParties", InformationRequestState.IN_PROGRESS, (permissions: AuthorActionPermissions) => permissions.parties.change],
        ["manageParties", InformationRequestState.IN_PROGRESS, (permissions: AuthorActionPermissions) => permissions.parties.reduceAccess],
        ["previewRecipients", InformationRequestState.IN_PROGRESS, (permissions: AuthorActionPermissions) => permissions.preview],
        ["manageClocks", InformationRequestState.IN_PROGRESS, (permissions: AuthorActionPermissions) => permissions.clocks.startOrResume],
        ["manageClocks", InformationRequestState.IN_PROGRESS, (permissions: AuthorActionPermissions) => permissions.clocks.pauseOrExtend],
        ["scheduleFollowUps", InformationRequestState.CLOSED, (permissions: AuthorActionPermissions) => permissions.followUps.schedule],
        ["scheduleFollowUps", InformationRequestState.DRAFT, (permissions: AuthorActionPermissions) => permissions.followUps.schedule],
        ["createFollowUps", InformationRequestState.CLOSED, (permissions: AuthorActionPermissions) => permissions.followUps.create],
        ["promoteFacts", InformationRequestState.CLOSED, (permissions: AuthorActionPermissions) => permissions.outcomes.promote],
        ["revokeFacts", InformationRequestState.CLOSED, (permissions: AuthorActionPermissions) => permissions.outcomes.revoke],
        ["recordDecisions", InformationRequestState.CLOSED, (permissions: AuthorActionPermissions) => permissions.outcomes.decide],
    ] as const)("withholds the control when the server refuses %s, even under ACTIVE standing", (action, state, read) => {
        expect(read(permissionsUnder(InformationRequestExecutionStandingKind.ACTIVE, state))).toBe(true);
        expect(read(permissionsUnder(InformationRequestExecutionStandingKind.ACTIVE, state, {
            ...everyAuthorAction,
            [action]: false
        })))
            .toBe(false);
    });
});
