import FieldValueEditor from "../../exchanges/components/exchange-fields-tab/FieldValueEditor.tsx";
import {toFieldElementId} from "../../exchanges/components/exchange-fields-tab/fieldLayoutUtils.ts";
import {
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseDto,
    InformationRequestTemplateRequirementDto,
    SchemaFieldBindingDto,
} from "../../models/models.tsx";
import RequirementEvidencePanel from "../requirement-evidence/requirement-evidence-panel/RequirementEvidencePanel.tsx";
import ReusableAnswerSlot from "../reusable-answer/reusable-answer-slot/ReusableAnswerSlot.tsx";
import TextField from "../shared/text-field/TextField.tsx";
import {useInformationRequestStructuredResponseWorkspaceStyles} from "./InformationRequestStructuredResponseWorkspaceStyles.tsx";
import ResponseDispositionChoice from "./response-disposition-choice/ResponseDispositionChoice.tsx";
import ResponseRequirementHeader from "./response-requirement-header/ResponseRequirementHeader.tsx";
import {answersWithValue, ResponseAnswerEdit, ResponseAnswerEdits} from "./responseAnswerState.ts";
import {ResponseEdits, responseForFieldRequirement, responseKey, shownFieldValues} from "./structuredResponseWorkspaceState.ts";
import {ResponseETagResult} from "./StructuredResponseWorkspaceTypes.ts";

interface Props
{
    requestId: string;
    occurrencePath: string;
    requirement: InformationRequestTemplateRequirementDto;
    bindings: SchemaFieldBindingDto[];
    responses: InformationRequestResponseDto[];
    edits: ResponseEdits;
    setEdits: (edits: (previous: ResponseEdits) => ResponseEdits) => void;
    answers: ResponseAnswerEdits;
    setAnswers: (answers: (previous: ResponseAnswerEdits) => ResponseAnswerEdits) => void;
    responseETag: string;
    busy: boolean;
    onResult: (result: ResponseETagResult) => void;
    onCommandStart: () => void;
    onCommandFailure: (error: unknown) => void;
}

const without = <T, >(record: Record<string, T>, key: string): Record<string, T> =>
    Object.fromEntries(Object.entries(record).filter(([candidate]) => candidate !== key));

const currentDisposition = (
    requirement: InformationRequestTemplateRequirementDto,
    response: InformationRequestResponseDto | undefined,
    answer: ResponseAnswerEdit | undefined,
): InformationRequestResponseDisposition =>
{
    const permitted = requirement.permittedDispositions;
    if (answer?.disposition) return answer.disposition;
    if (response && permitted.includes(response.disposition)) return response.disposition;
    return permitted.includes(InformationRequestResponseDisposition.PROVIDED) ? InformationRequestResponseDisposition.PROVIDED : permitted[0];
};

const StructuredResponseRequirement = ({requestId, occurrencePath, requirement, bindings, responses, edits, setEdits, answers, setAnswers, ...commands}: Props) =>
{
    const styles = useInformationRequestStructuredResponseWorkspaceStyles();
    const response = responseForFieldRequirement(responses, requirement, occurrencePath);
    const elementId = toFieldElementId(`${occurrencePath}-${requirement.requirementKey}`);
    const key = responseKey(requirement.id, occurrencePath);
    const disposition = currentDisposition(requirement, response, answers[key]);
    const setAnswer = (change: ResponseAnswerEdit) => setAnswers(previous => ({...previous, [key]: {...previous[key], ...change}}));
    const choice = requirement.permittedDispositions.length > 1 && (
        <ResponseDispositionChoice elementId={elementId}
                                   value={disposition}
                                   options={requirement.permittedDispositions}
                                   onChange={chosen => setAnswer({disposition: chosen})}/>
    );
    const explanation = !answersWithValue(disposition) && (
        <TextField id={`information-request-response-narrative-${elementId}`}
                   label={"Explain your answer"}
                   multiline={true}
                   maxLength={4000}
                   value={answers[key]?.narrative ?? response?.narrative ?? ""}
                   onChange={narrative => setAnswer({narrative})}/>
    );

    if (requirement.requirementType === InformationRequestRequirementType.DOCUMENT)
    {
        if (!response) return null;
        return (
            <div id={`information-request-response-requirement-${elementId}`}
                 className={styles.requirement}>
                {answersWithValue(disposition)
                    ? (
                        <RequirementEvidencePanel requestId={requestId}
                                                  requirementId={response.informationRequestRequirementId}
                                                  prompt={requirement.prompt}
                                                  elementId={elementId}/>
                    )
                    : (
                        <ResponseRequirementHeader requirement={requirement}
                                                   elementId={elementId}/>
                    )}
                {choice}
                {explanation}
            </div>
        );
    }

    const binding = bindings.find(candidate => candidate.fieldDefinitionId === requirement.collectedFieldDefinitionId);
    if (!binding) return null;
    const shown = shownFieldValues(binding, occurrencePath, requirement.id, response, edits);

    return (
        <div id={`information-request-response-requirement-${elementId}`}
             className={styles.requirement}>
            <ResponseRequirementHeader requirement={requirement}
                                       elementId={elementId}/>
            {choice}
            {answersWithValue(disposition) && (
                <ReusableAnswerSlot elementId={elementId}
                                    requestId={requestId}
                                    response={response}
                                    shownValue={shown[binding.fieldContractId]}
                                    onApplied={() =>
                                    {
                                        setEdits(previous => without(previous, key));
                                        setAnswers(previous => without(previous, key));
                                    }}
                                    {...commands}/>
            )}
            {answersWithValue(disposition) && (
                <FieldValueEditor binding={binding}
                                  value={shown[binding.fieldContractId]}
                                  onChange={value => setEdits(previous => ({
                                      ...previous,
                                      [key]: {...previous[key], [binding.fieldContractId]: value},
                                  }))}
                                  showLabel={false}
                                  labelledBy={`information-request-response-prompt-${elementId}`}/>
            )}
            {explanation}
        </div>
    );
};

export default StructuredResponseRequirement;
