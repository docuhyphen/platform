import {
    InformationRequestAuthorActionsDto,
    InformationRequestExecutionStandingDto,
    InformationRequestExecutionStandingKind,
    InformationRequestState,
} from "../../../models/models.tsx";
import {
    mayChangeUnderStanding,
    mayIssueUnderStanding,
    mayRecoverAsOwnerUnderStanding,
    mayWithdrawAsOwnerUnderStanding,
} from "../../shared/executionStandingText.ts";
import {schedulesFollowUps, startsFollowUps} from "../follow-up-panel/followUpAvailability.ts";

export interface LifecycleActionPermissions {
    issue: boolean;
    cancel: boolean;
    supersede: boolean;
}

export interface PartyActionPermissions {
    change: boolean;
    reduceAccess: boolean;
    issueLinks: boolean;
}

export interface ClockActionPermissions {
    startOrResume: boolean;
    pauseOrExtend: boolean;
}

export interface FollowUpActionPermissions {
    schedule: boolean;
    create: boolean;
}

export interface OutcomeActionPermissions {
    promote: boolean;
    revoke: boolean;
    decide: boolean;
}

export interface AuthorActionPermissions {
    lifecycle: LifecycleActionPermissions;
    parties: PartyActionPermissions;
    clocks: ClockActionPermissions;
    completionGate: boolean;
    followUps: FollowUpActionPermissions;
    outcomes: OutcomeActionPermissions;
    preview: boolean;
}

const UNFINISHED_STATES = new Set([InformationRequestState.DRAFT, InformationRequestState.ISSUED, InformationRequestState.IN_PROGRESS]);
const FACT_STATES = new Set([InformationRequestState.ISSUED, InformationRequestState.IN_PROGRESS, InformationRequestState.CLOSED]);

const NO_SERVER_ACTIONS: InformationRequestAuthorActionsDto = {
    issue: false,
    cancel: false,
    supersede: false,
    changeCompletionGate: false,
    manageParties: false,
    previewRecipients: false,
    manageClocks: false,
    scheduleFollowUps: false,
    createFollowUps: false,
    promoteFacts: false,
    revokeFacts: false,
    recordDecisions: false,
    issueAccessLinks: false,
};

export const NO_AUTHOR_ACTIONS: AuthorActionPermissions = {
    lifecycle: {issue: false, cancel: false, supersede: false},
    parties: {change: false, reduceAccess: false, issueLinks: false},
    clocks: {startOrResume: false, pauseOrExtend: false},
    completionGate: false,
    followUps: {schedule: false, create: false},
    outcomes: {promote: false, revoke: false, decide: false},
    preview: false,
};

export const authorActionPermissions = (
    state: InformationRequestState,
    standing: InformationRequestExecutionStandingDto,
    actions: InformationRequestAuthorActionsDto | null | undefined,
    gatesExchangeClosure = false,
): AuthorActionPermissions => {
    const allowed = actions ?? NO_SERVER_ACTIONS;
    const unfinished = UNFINISHED_STATES.has(state);
    const changes = mayChangeUnderStanding(standing);
    const recovers = mayRecoverAsOwnerUnderStanding(standing);
    const withdraws = mayWithdrawAsOwnerUnderStanding(standing);
    const startsNewWork = standing.kind === InformationRequestExecutionStandingKind.ACTIVE;
    return {
        lifecycle: {
            issue: allowed.issue && state === InformationRequestState.DRAFT && mayIssueUnderStanding(standing),
            cancel: allowed.cancel && unfinished && withdraws,
            supersede: allowed.supersede && unfinished && recovers,
        },
        parties: {
            change: allowed.manageParties && unfinished && changes,
            reduceAccess: allowed.manageParties && unfinished,
            issueLinks: allowed.issueAccessLinks === true && unfinished && changes,
        },
        clocks: {
            startOrResume: allowed.manageClocks && unfinished && changes,
            pauseOrExtend: allowed.manageClocks && unfinished && recovers,
        },
        completionGate: allowed.changeCompletionGate && unfinished && (recovers || (withdraws && gatesExchangeClosure)),
        followUps: {
            schedule: allowed.scheduleFollowUps && startsNewWork && schedulesFollowUps(state),
            create: allowed.createFollowUps && startsNewWork && startsFollowUps(state),
        },
        outcomes: {
            promote: allowed.promoteFacts && changes && FACT_STATES.has(state),
            revoke: allowed.revokeFacts && state !== InformationRequestState.DRAFT,
            decide: allowed.recordDecisions && changes && state !== InformationRequestState.DRAFT,
        },
        preview: allowed.previewRecipients,
    };
};
