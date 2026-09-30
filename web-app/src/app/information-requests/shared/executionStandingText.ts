import {
    InformationRequestExecutionStandingDto,
    InformationRequestExecutionStandingKind,
    InformationRequestStandingReason,
} from "../../models/models.tsx";

export interface ExecutionStandingNoticeText
{
    intent: "warning" | "info";
    text: string;
}

export const standingReasonSentence: Record<InformationRequestStandingReason, string> = {
    [InformationRequestStandingReason.FEATURE_NOT_INCLUDED]: "The owner's plan does not include Information Requests.",
    [InformationRequestStandingReason.TRIAL_ENDED]: "The owner's trial has ended.",
    [InformationRequestStandingReason.SUBSCRIPTION_PAST_DUE]: "Payment for the owner's subscription is overdue.",
    [InformationRequestStandingReason.SUBSCRIPTION_CANCELED]: "The owner's subscription has been canceled.",
    [InformationRequestStandingReason.SUBSCRIPTION_SUSPENDED]: "The owner's account is suspended.",
    [InformationRequestStandingReason.EXECUTION_GRANT_REVOKED]: "A platform administrator stopped this request.",
};

const badges: Record<InformationRequestExecutionStandingKind, string | null> = {
    [InformationRequestExecutionStandingKind.ACTIVE]: null,
    [InformationRequestExecutionStandingKind.NEW_WORK_UNAVAILABLE]: "Read only",
    [InformationRequestExecutionStandingKind.CONTINUING_AFTER_LAPSE]: "Continuing as issued",
    [InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED]: "Changes paused",
    [InformationRequestExecutionStandingKind.EXECUTION_GRANT_REVOKED]: "Stopped",
};

const withReason = (text: string, standing: InformationRequestExecutionStandingDto): string =>
    standing.reason ? `${text} ${standingReasonSentence[standing.reason]}` : text;

export const executionStandingBadge = (standing: InformationRequestExecutionStandingDto): string | null =>
    badges[standing.kind];

export const mayChangeUnderStanding = (standing: InformationRequestExecutionStandingDto): boolean =>
    standing.kind === InformationRequestExecutionStandingKind.ACTIVE ||
    standing.kind === InformationRequestExecutionStandingKind.CONTINUING_AFTER_LAPSE;

export const executionStandingNotice = (standing: InformationRequestExecutionStandingDto): ExecutionStandingNoticeText | null =>
{
    switch (standing.kind)
    {
        case InformationRequestExecutionStandingKind.ACTIVE:
            return null;
        case InformationRequestExecutionStandingKind.NEW_WORK_UNAVAILABLE:
            return {
                intent: "warning",
                text: withReason("This draft cannot be issued or changed right now. It stays readable.", standing),
            };
        case InformationRequestExecutionStandingKind.CONTINUING_AFTER_LAPSE:
            return {
                intent: "info",
                text: withReason(
                    "This request continues as it was issued: its parties can still answer and review, but new requests and follow-ups are unavailable.",
                    standing,
                ),
            };
        case InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED:
            return {
                intent: "warning",
                text: withReason("Changes to this request are paused. You can still read and export what was recorded.", standing),
            };
        case InformationRequestExecutionStandingKind.EXECUTION_GRANT_REVOKED:
            return {
                intent: "warning",
                text: withReason("This request was stopped. You can still read and export what was recorded.", standing),
            };
    }
};
