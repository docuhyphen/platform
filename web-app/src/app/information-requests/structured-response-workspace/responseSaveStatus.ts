import type {InformationRequestItemRefusalDto} from "../../models/models.tsx";
import {formatInformationRequestCount} from "../shared/informationRequestFormatting.ts";

export const STALE_MESSAGE = "These answers changed somewhere else. Your changes are kept: review them against the latest answers, then choose Save responses.";

export const STALE_COMMAND_MESSAGE = "These answers changed somewhere else, so that change was not made. The latest answers are shown: try it again.";

export const VERIFICATION_LAPSED_MESSAGE = "A protected question needs you to verify again. Your answer to it stays in this tab until you do.";

export const RESPONDENT_CHANGED_MESSAGE = "You are no longer responding as the person who entered these answers, so they were not saved. They stay in this browser tab for that person.";

export const PARENT_STATE_MESSAGE = "The Exchange holding this request is not accepting answers now, for example because it has not been " +
    "accepted yet or it has ended. Your unsaved changes stay in this tab.";

const PARENT_STATE_REASON = "INFORMATION_REQUEST_PARENT_STATE_INVALID";

const GENERIC_COMMAND_FAILURE_MESSAGE = "The command could not be completed.";

interface SaveStatusInput
{
    readOnly?: boolean;
    busy: boolean;
    stale: boolean;
    pending: boolean;
    saved: boolean;
    refused?: boolean;
    awaitingExplanation?: number;
}

const awaitingText = (pending: boolean, awaiting: number): string => {
    if (pending) {
        return awaiting === 1
            ? "Unsaved changes. Saving shortly, except an answer that still needs its explanation."
            : `Unsaved changes. Saving shortly, except ${formatInformationRequestCount(awaiting)} answers that still need an explanation.`;
    }
    return awaiting === 1
        ? "Unsaved changes. Explain your answer so it can be saved."
        : `Unsaved changes. Explain ${formatInformationRequestCount(awaiting)} answers so they can be saved.`;
};

export const saveStatusText = ({
                                   readOnly = false,
                                   busy,
                                   stale,
                                   pending,
                                   saved,
                                   refused = false,
                                   awaitingExplanation = 0
                               }: SaveStatusInput): string =>
{
    if (readOnly) return "Answers cannot be changed now.";
    if (busy) return "Saving your answers.";
    if (stale) return STALE_MESSAGE;
    if (pending && refused) return "Unsaved changes. Choose Save responses to try again.";
    if (awaitingExplanation > 0) return awaitingText(pending, awaitingExplanation);
    if (pending) return "Unsaved changes. Saving shortly.";
    return saved ? "All changes saved." : "";
};

const itemProblemsText = (problems: unknown, undisclosedProblemCount: unknown): string[] => {
    const messages = Array.isArray(problems)
        ? problems
            .map(problem => (problem as { message?: unknown } | null)?.message)
            .filter((message): message is string => typeof message === "string" && message.trim().length > 0)
        : [];
    const hidden = typeof undisclosedProblemCount === "number" && undisclosedProblemCount > 0
        ? [`${undisclosedProblemCount} more ${undisclosedProblemCount === 1 ? "item" : "items"} could not be shown.`]
        : [];
    return [...messages, ...hidden];
};

export const commandErrorMessage = (error: unknown): string =>
{
    if (typeof error === "string") return error;
    if (error instanceof Error) return error.message;
    if (typeof error !== "object" || error === null) return GENERIC_COMMAND_FAILURE_MESSAGE;
    const candidate = error as Partial<Record<keyof InformationRequestItemRefusalDto, unknown>> & {
        message?: unknown;
    };
    if (candidate.reasonCode === PARENT_STATE_REASON) return PARENT_STATE_MESSAGE;
    if (typeof candidate.errorMessage === "string" && candidate.errorMessage.trim()) {
        return [candidate.errorMessage, ...itemProblemsText(candidate.problems, candidate.undisclosedProblemCount)].join(" ");
    }
    if (typeof candidate.message === "string" && candidate.message.trim()) return candidate.message;
    if (typeof candidate.reasonCode === "string" && candidate.reasonCode.trim()) return candidate.reasonCode;
    return GENERIC_COMMAND_FAILURE_MESSAGE;
};
