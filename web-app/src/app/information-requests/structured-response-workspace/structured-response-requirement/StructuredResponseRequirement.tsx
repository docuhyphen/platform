import {
    InformationRequestRequirementType,
    InformationRequestResponseDto,
    InformationRequestTemplateRequirementDto,
    SchemaFieldBindingDto,
} from "../../../models/models.tsx";
import {requirementElementKey} from "../../shared/responseElementKeys.ts";
import DocumentRequirementHeading from "../document-requirement-heading/DocumentRequirementHeading.tsx";
import ResponseDispositionChoice from "../response-disposition-choice/ResponseDispositionChoice.tsx";
import ResponseNarrativeInput from "../response-narrative-input/ResponseNarrativeInput.tsx";
import ResponseRequirementHeader from "../response-requirement-header/ResponseRequirementHeader.tsx";
import ResponseStandingNotice from "../response-standing-notice/ResponseStandingNotice.tsx";
import StructuredResponseFieldAnswer from "../structured-response-field-answer/StructuredResponseFieldAnswer.tsx";
import SupportingEvidenceNote from "../supporting-evidence-note/SupportingEvidenceNote.tsx";
import {answersWithValue, currentDisposition, ResponseAnswerEdit, ResponseAnswerEdits} from "../responseAnswerState.ts";
import {ResponseEdits, responseForFieldRequirement, responseKey} from "../structuredResponseWorkspaceState.ts";
import {ResponseETagResult} from "../StructuredResponseWorkspaceTypes.ts";
import {requirementPromptId} from "./structuredResponseRequirementState.ts";
import {useStructuredResponseRequirementStyles} from "./StructuredResponseRequirementStyles.tsx";

interface Props {
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
    readOnly: boolean;
    onResult: (result: ResponseETagResult) => void;
    onCommandStart: () => void;
    onCommandFailure: (error: unknown) => void;
}

const StructuredResponseRequirement = ({
                                           requestId,
                                           occurrencePath,
                                           requirement,
                                           bindings,
                                           responses,
                                           edits,
                                           setEdits,
                                           answers,
                                           setAnswers,
                                           readOnly,
                                           ...commands
                                       }: Props) => {
    const styles = useStructuredResponseRequirementStyles();
    const response = responseForFieldRequirement(responses, requirement, occurrencePath);
    const locked = readOnly || Boolean(response?.editLock);
    const elementId = requirementElementKey(occurrencePath, requirement.requirementKey);
    const key = responseKey(requirement.id, occurrencePath);
    const answer = locked ? undefined : answers[key];
    const disposition = currentDisposition(requirement, response, answer);
    const document = requirement.requirementType === InformationRequestRequirementType.DOCUMENT;
    const promptId = requirementPromptId(elementId, document && answersWithValue(disposition));
    const setAnswer = (change: ResponseAnswerEdit) => setAnswers(previous => ({
        ...previous,
        [key]: {...previous[key], ...change}
    }));
    const binding = bindings.find(candidate => candidate.fieldDefinitionId === requirement.collectedFieldDefinitionId);

    if (!response || (!document && !binding)) return null;

    return (
        <div id={`information-request-response-requirement-${elementId}`}
             className={styles.requirement}>
            {document
                ? (
                    <DocumentRequirementHeading requestId={requestId}
                                                requirement={requirement}
                                                requirementId={response.informationRequestRequirementId}
                                                elementId={elementId}
                                                locked={locked}
                                                collecting={answersWithValue(disposition)}/>
                )
                : (
                    <ResponseRequirementHeader requirement={requirement}
                                               elementId={elementId}/>
                )}
            <SupportingEvidenceNote elementId={elementId}
                                    requirementId={response.informationRequestRequirementId}/>
            <ResponseStandingNotice elementId={elementId}
                                    response={response}
                                    kept={Boolean(answer?.reconfirmed)}
                                    onKeep={() => setAnswer({reconfirmed: true})}/>
            {requirement.permittedDispositions.length > 1 && (
                <ResponseDispositionChoice elementId={elementId}
                                           promptId={promptId}
                                           value={disposition}
                                           options={requirement.permittedDispositions}
                                           disabled={locked}
                                           onChange={chosen => setAnswer({disposition: chosen})}/>
            )}
            {!document && binding && answersWithValue(disposition) && (
                <StructuredResponseFieldAnswer requestId={requestId}
                                               elementId={elementId}
                                               answerKey={key}
                                               requirementId={requirement.id}
                                               occurrencePath={occurrencePath}
                                               response={response}
                                               binding={binding}
                                               edits={edits}
                                               locked={locked}
                                               setEdits={setEdits}
                                               setAnswers={setAnswers}
                                               {...commands}/>
            )}
            <ResponseNarrativeInput elementId={elementId}
                                    promptId={promptId}
                                    disposition={disposition}
                                    value={answer?.narrative ?? response.narrative ?? ""}
                                    locked={locked}
                                    onChange={narrative => setAnswer({narrative})}/>
        </div>
    );
};

export default StructuredResponseRequirement;
