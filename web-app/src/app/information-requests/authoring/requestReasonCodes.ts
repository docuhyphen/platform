import {ChoiceOption} from "../shared/choice-select/choiceOptions.ts";

export const CANCEL_REASON_OPTIONS: ChoiceOption<string>[] = [
    {value: "no-longer-needed", label: "No longer needed"},
    {value: "created-in-error", label: "Created in error"},
    {value: "handled-another-way", label: "Handled another way"},
    {value: "respondent-unavailable", label: "Respondent unavailable"},
];

export const SUPPLEMENT_REASON_OPTIONS: ChoiceOption<string>[] = [
    {value: "more-information-needed", label: "More information is needed"},
    {value: "document-missing", label: "A document is missing or incomplete"},
    {value: "document-expiring", label: "A document has expired or is about to"},
    {value: "answer-unclear", label: "An answer needs clarifying"},
];

export const SUPERSEDE_REASON_OPTIONS: ChoiceOption<string>[] = [
    {value: "requirements-changed", label: "What is needed has changed"},
    {value: "parties-changed", label: "The parties have changed"},
    {value: "request-corrected", label: "The request needed correcting"},
];
