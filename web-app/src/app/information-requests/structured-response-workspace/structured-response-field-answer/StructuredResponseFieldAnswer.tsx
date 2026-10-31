import {InformationRequestResponseDto, SchemaFieldBindingDto} from "../../../models/models.tsx";
import StructuredFieldValueInput from "../structured-field-value-input/StructuredFieldValueInput.tsx";
import {ResponseAnswerEdits} from "../responseAnswerState.ts";
import {ResponseEdits, shownFieldValues} from "../structuredResponseWorkspaceState.ts";
import {ResponseETagResult} from "../StructuredResponseWorkspaceTypes.ts";
import {keepingNarrative, without} from "../structured-response-requirement/structuredResponseRequirementState.ts";

interface Props {
    requestId: string;
    elementId: string;
    answerKey: string;
    requirementId: string;
    occurrencePath: string;
    response: InformationRequestResponseDto;
    binding: SchemaFieldBindingDto;
    edits: ResponseEdits;
    locked: boolean;
    setEdits: (edits: (previous: ResponseEdits) => ResponseEdits) => void;
    setAnswers: (answers: (previous: ResponseAnswerEdits) => ResponseAnswerEdits) => void;
    responseETag: string;
    busy: boolean;
    onResult: (result: ResponseETagResult) => void;
    onCommandStart: () => void;
    onCommandFailure: (error: unknown) => void;
}

const NO_EDITS: ResponseEdits = {};

const StructuredResponseFieldAnswer = ({
                                           elementId,
                                           answerKey,
                                           requirementId,
                                           occurrencePath,
                                           response,
                                           binding,
                                           edits,
                                           locked,
                                           setEdits,
                                           setAnswers,
                                           ...commands
                                       }: Props) => {
    const shown = shownFieldValues(binding, occurrencePath, requirementId, response, locked ? NO_EDITS : edits);

    return (
        <StructuredFieldValueInput elementId={elementId}
                                   response={response}
                                   binding={binding}
                                   value={shown[binding.fieldContractId]}
                                   locked={locked}
                                   onChange={value => setEdits(previous => ({
                                       ...previous,
                                       [answerKey]: {...previous[answerKey], [binding.fieldContractId]: value},
                                   }))}
                                   onApplied={() => {
                                       setEdits(previous => without(previous, answerKey));
                                       setAnswers(previous => keepingNarrative(previous, answerKey));
                                   }}
                                   {...commands}/>
    );
};

export default StructuredResponseFieldAnswer;
