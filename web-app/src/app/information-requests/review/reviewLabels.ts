import {BadgeProps} from "@fluentui/react-components";
import {
    InformationRequestCorrectionState,
    InformationRequestFindingCorrectionScope,
    InformationRequestFindingSeverity,
    InformationRequestReviewAggregation,
    InformationRequestReviewItemStanding,
    InformationRequestReviewKind,
    InformationRequestReviewOutcome,
    InformationRequestReviewStageState,
    InformationRequestReviewStageStandingDto,
    InformationRequestReviewState,
    InformationRequestReviewVisibility,
} from "../../models/models.tsx";

type BadgeColor = NonNullable<BadgeProps["color"]>;

export const reviewStatePresentation: Record<InformationRequestReviewState, {label: string; color: BadgeColor}> = {
    [InformationRequestReviewState.PENDING]: {label: "Waiting for a reviewer", color: "informative"},
    [InformationRequestReviewState.IN_REVIEW]: {label: "In review", color: "brand"},
    [InformationRequestReviewState.CHANGES_REQUESTED]: {label: "Changes requested", color: "warning"},
    [InformationRequestReviewState.REJECTED]: {label: "Rejected", color: "danger"},
    [InformationRequestReviewState.SATISFIED]: {label: "Satisfied", color: "success"},
    [InformationRequestReviewState.SATISFIED_WITH_EXCEPTION]: {label: "Satisfied with exception", color: "success"},
    [InformationRequestReviewState.WITHDRAWN]: {label: "Withdrawn", color: "subtle"},
};

export const reviewOutcomeLabels: Record<InformationRequestReviewOutcome, string> = {
    [InformationRequestReviewOutcome.SATISFIED]: "Satisfied",
    [InformationRequestReviewOutcome.SATISFIED_WITH_EXCEPTION]: "Satisfied with exception",
    [InformationRequestReviewOutcome.WAIVED]: "Waived",
    [InformationRequestReviewOutcome.CHANGES_REQUIRED]: "Changes required",
    [InformationRequestReviewOutcome.REJECTED]: "Rejected",
};

export const reviewKindLabels: Record<InformationRequestReviewKind, string> = {
    [InformationRequestReviewKind.INITIAL]: "Review",
    [InformationRequestReviewKind.RESUBMISSION]: "Review of a resubmission",
    [InformationRequestReviewKind.RECONSIDERATION]: "Reconsideration",
    [InformationRequestReviewKind.APPEAL]: "Appeal",
};

export const findingSeverityLabels: Record<InformationRequestFindingSeverity, string> = {
    [InformationRequestFindingSeverity.OBSERVATION]: "Observation",
    [InformationRequestFindingSeverity.MINOR]: "Minor",
    [InformationRequestFindingSeverity.MAJOR]: "Major",
    [InformationRequestFindingSeverity.CRITICAL]: "Critical",
};

export const correctionScopeLabels: Record<InformationRequestFindingCorrectionScope, string> = {
    [InformationRequestFindingCorrectionScope.NONE]: "Nothing to correct",
    [InformationRequestFindingCorrectionScope.RESPONSE]: "Correct the answer",
    [InformationRequestFindingCorrectionScope.EVIDENCE_VERSION]: "Replace one file",
    [InformationRequestFindingCorrectionScope.ADDITIONAL_EVIDENCE]: "Add another file",
};

export const visibilityLabels: Record<InformationRequestReviewVisibility, string> = {
    [InformationRequestReviewVisibility.RESPONDENT_VISIBLE]: "Shown to the respondent",
    [InformationRequestReviewVisibility.REVIEWERS_ONLY]: "Reviewers only",
};

export const correctionStateLabels: Record<InformationRequestCorrectionState, string> = {
    [InformationRequestCorrectionState.OPEN]: "Open for correction",
    [InformationRequestCorrectionState.RESUBMITTED]: "Resubmitted",
    [InformationRequestCorrectionState.SUPERSEDED]: "Superseded",
};

export const stageStateLabels: Record<InformationRequestReviewStageState, string> = {
    [InformationRequestReviewStageState.WAITING]: "Waiting for an earlier stage",
    [InformationRequestReviewStageState.OPEN]: "Open",
    [InformationRequestReviewStageState.SETTLED]: "Settled",
};

export const itemStandingLabels: Record<InformationRequestReviewItemStanding, string> = {
    [InformationRequestReviewItemStanding.PENDING]: "Waiting for decisions",
    [InformationRequestReviewItemStanding.UNDERSTAFFED]: "Needs more reviewers",
    [InformationRequestReviewItemStanding.TIED]: "Tied, needs an override",
    [InformationRequestReviewItemStanding.DECIDED]: "Decided",
};

export const aggregationLabel = (stage: InformationRequestReviewStageStandingDto): string =>
{
    switch (stage.aggregation)
    {
        case InformationRequestReviewAggregation.ALL:
            return `Every reviewer decides, at least ${stage.minimumReviewerCount}`;
        case InformationRequestReviewAggregation.ANY:
            return "The first reviewer decides";
        case InformationRequestReviewAggregation.QUORUM:
            return `${stage.quorumCount ?? stage.minimumReviewerCount} matching decisions settle an item`;
        case InformationRequestReviewAggregation.CONSENSUS:
            return `Every reviewer must agree, at least ${stage.minimumReviewerCount}`;
    }
};

export const outcomeNeedsFinding = (outcome?: InformationRequestReviewOutcome): boolean =>
    outcome === InformationRequestReviewOutcome.CHANGES_REQUIRED || outcome === InformationRequestReviewOutcome.REJECTED;

export const outcomeNeedsNarrative = (outcome?: InformationRequestReviewOutcome): boolean =>
    outcome === InformationRequestReviewOutcome.SATISFIED_WITH_EXCEPTION || outcome === InformationRequestReviewOutcome.WAIVED;

export const reviewValueText = (value: unknown): string =>
{
    if (value === undefined || value === null) return "No value";
    if (Array.isArray(value)) return value.map(reviewValueText).join(", ");
    if (typeof value === "object") return JSON.stringify(value);
    return String(value);
};
