import {
    HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED,
    HiddenResponseClearTargetDto,
    InformationRequestTemplateRequirementDto,
} from "../../models/models.tsx";
import {HiddenClearConfirmation} from "./structuredResponseWorkspaceState.ts";

export const HIDDEN_CLEAR_REFUSAL_MESSAGE = "Saving this change clears answers that no longer apply. Confirm each one, then save again.";

const isClearTarget = (candidate: unknown): candidate is HiddenResponseClearTargetDto => {
    if (typeof candidate !== "object" || candidate === null) return false;
    const target = candidate as Record<string, unknown>;
    return typeof target.requirementId === "string" &&
        typeof target.sourceTemplateBindingId === "string" &&
        typeof target.occurrencePath === "string";
};

export const hiddenClearTargetsOf = (refusal: unknown): HiddenResponseClearTargetDto[] => {
    if (typeof refusal !== "object" || refusal === null) return [];
    const body = refusal as { reasonCode?: unknown; hiddenResponseClears?: unknown };
    if (body.reasonCode !== HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED || !Array.isArray(body.hiddenResponseClears)) return [];
    return body.hiddenResponseClears.filter(isClearTarget);
};

export const confirmationsForTargets = (
    targets: HiddenResponseClearTargetDto[],
    requirements: InformationRequestTemplateRequirementDto[],
): HiddenClearConfirmation[] =>
    targets.map(target => ({
        requirementId: target.requirementId,
        prompt: requirements.find(requirement => requirement.id === target.sourceTemplateBindingId)?.prompt ?? "An answer that no longer applies",
        occurrencePath: target.occurrencePath,
    }));

export const mergeConfirmations = (
    first: HiddenClearConfirmation[],
    second: HiddenClearConfirmation[],
): HiddenClearConfirmation[] =>
    [...first, ...second].filter((confirmation, index, all) =>
        all.findIndex(other => other.requirementId === confirmation.requirementId) === index);
