import {InformationRequestResponseDisposition} from "../../../models/models.tsx";
import {asksForExplanation, ResponseAnswerEdits} from "../responseAnswerState.ts";

export interface NarrativeField {
    label: string;
    hint?: string;
    required?: boolean;
}

const EXPLANATION: NarrativeField = {label: "Explain your answer", required: true};

const NOTE: NarrativeField = {label: "Note", hint: "Optional. Shown with your answer."};

export const requirementPromptId = (elementId: string, collectsFiles: boolean): string =>
    collectsFiles ? `information-request-evidence-${elementId}-PANEL-prompt` : `information-request-response-prompt-${elementId}`;

export const without = <T, >(record: Record<string, T>, key: string): Record<string, T> =>
    Object.fromEntries(Object.entries(record).filter(([candidate]) => candidate !== key));

export const keepingNarrative = (answers: ResponseAnswerEdits, key: string): ResponseAnswerEdits => {
    const narrative = answers[key]?.narrative;
    const others = without(answers, key);
    return narrative === undefined ? others : {...others, [key]: {narrative}};
};

export const narrativeFieldFor = (disposition: InformationRequestResponseDisposition): NarrativeField | null => {
    if (asksForExplanation(disposition)) return EXPLANATION;
    return disposition === InformationRequestResponseDisposition.PROVIDED ? NOTE : null;
};
