import {
    InformationRequestEvidenceConformancePolicy,
    InformationRequestEvidenceWaiverPolicy,
    InformationRequestReviewPolicy,
    InformationRequestTemplateRequirementRequest,
} from "../../models/models.tsx";

export type TemplateDraftTarget =
    | {panel: "settings"}
    | {panel: "sections"; sectionIndex?: number; requirementIndex?: number}
    | {panel: "groups"; groupIndex: number}
    | {panel: "conditions"; ruleIndex: number}
    | {panel: "review"; stageIndex?: number};

export interface TemplateDraftProblem
{
    key: string;
    message: string;
    target: TemplateDraftTarget;
}

export type TemplateProblemSink = (message: string, target: TemplateDraftTarget) => void;

export const MACHINE_KEY_PATTERN = /^[a-z0-9]([a-z0-9-]*[a-z0-9])?$/;

export const keyShapeMessage = (label: string, key: string): string =>
    `${label} "${key}" must use lowercase letters, digits, and inner hyphens.`;

export const requirementLabel = (requirement: InformationRequestTemplateRequirementRequest): string =>
    requirement.prompt.trim() || requirement.requirementKey;

export const routesReview = (requirement: InformationRequestTemplateRequirementRequest): boolean =>
    (requirement.reviewPolicy ?? InformationRequestReviewPolicy.NOT_REQUIRED) !== InformationRequestReviewPolicy.NOT_REQUIRED
    || requirement.evidencePolicy?.conformancePolicy === InformationRequestEvidenceConformancePolicy.DEFICIENCY_REVIEWABLE
    || requirement.evidencePolicy?.waiverPolicy === InformationRequestEvidenceWaiverPolicy.REVIEW_APPROVAL_REQUIRED;
