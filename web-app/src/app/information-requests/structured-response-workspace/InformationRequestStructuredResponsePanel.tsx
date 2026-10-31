import {useMemo} from "react";
import {
    InformationRequestDto,
    InformationRequestGroupOccurrenceDto,
    InformationRequestResponseDto,
    InformationRequestSupportingEvidenceLinkDto,
    InformationRequestTemplateGroupDto,
    InformationRequestTemplateRequirementDto,
    SchemaFieldBindingDto,
} from "../../models/models.tsx";
import {KeptEvidenceUploads} from "../requirement-evidence/keptEvidenceUploads.ts";
import {RequirementEvidenceContext} from "../requirement-evidence/RequirementEvidenceContext.ts";
import {requirementEvidenceCommands} from "../requirement-evidence/requirementEvidenceCommands.ts";
import {ReusableAnswerContext} from "../reusable-answer/ReusableAnswerContext.ts";
import ReusableAnswerLoadError from "../reusable-answer/reusable-answer-load-error/ReusableAnswerLoadError.tsx";
import {useReusableAnswers} from "../reusable-answer/useReusableAnswers.ts";
import {useCommandKeys} from "../shared/useStableIdempotencyKey.ts";
import InformationRequestStructuredResponseWorkspace from "./InformationRequestStructuredResponseWorkspace.tsx";
import {structuredResponseCommands} from "./structuredResponseCommands.ts";

interface Props
{
    request: InformationRequestDto;
    respondentKey: string;
    responseETag: string;
    groups: InformationRequestTemplateGroupDto[];
    creatableGroupKeys: string[];
    occurrences: InformationRequestGroupOccurrenceDto[];
    requirements: InformationRequestTemplateRequirementDto[];
    bindings: SchemaFieldBindingDto[];
    responses: InformationRequestResponseDto[];
    supportingEvidenceLinks?: InformationRequestSupportingEvidenceLinkDto[];
    evidenceUploadAvailable: boolean;
    evidenceMalwareScanning: boolean;
    readOnly?: boolean;
    accessLinkToken?: string;
    keptUploads?: KeptEvidenceUploads;
    onRefresh: () => void | Promise<void>;
    onAccessSessionEnded?: (refusal: unknown) => void;
}

const InformationRequestStructuredResponsePanel = ({
    request,
                                                       respondentKey,
    responseETag,
    groups,
                                                       creatableGroupKeys,
    occurrences,
    requirements,
    bindings,
    responses,
                                                       supportingEvidenceLinks,
    evidenceUploadAvailable,
    evidenceMalwareScanning,
                                                       readOnly,
    accessLinkToken,
                                                       keptUploads,
    onRefresh,
                                                       onAccessSessionEnded,
}: Props) =>
{
    const commandKeys = useCommandKeys();
    const commands = useMemo(
        () => structuredResponseCommands({accessLinkToken, commandKeys}),
        [accessLinkToken, commandKeys],
    );
    const evidence = useMemo(
        () => ({
            uploadAvailable: evidenceUploadAvailable,
            malwareScanning: evidenceMalwareScanning,
            commands: requirementEvidenceCommands({accessLinkToken, commandKeys, onAccessSessionEnded}),
            keptUploads,
        }),
        [accessLinkToken, commandKeys, evidenceMalwareScanning, evidenceUploadAvailable, keptUploads, onAccessSessionEnded],
    );
    const reusableAnswers = useReusableAnswers(request.id, responseETag, accessLinkToken);

    return (
        <RequirementEvidenceContext.Provider value={evidence}>
            <ReusableAnswerContext.Provider value={reusableAnswers}>
                <ReusableAnswerLoadError/>
                <InformationRequestStructuredResponseWorkspace request={request}
                                                               respondentKey={respondentKey}
                                                               responseETag={responseETag}
                                                               groups={groups}
                                                               creatableGroupKeys={creatableGroupKeys}
                                                               occurrences={occurrences}
                                                               requirements={requirements}
                                                               bindings={bindings}
                                                               responses={responses}
                                                               supportingEvidenceLinks={supportingEvidenceLinks}
                                                               readOnly={readOnly}
                                                               onSaveResponses={commands.saveResponses}
                                                               onAddOccurrence={commands.addOccurrence}
                                                               onRemoveOccurrence={commands.removeOccurrence}
                                                               onReorderOccurrences={commands.reorderOccurrences}
                                                               onRefresh={onRefresh}/>
            </ReusableAnswerContext.Provider>
        </RequirementEvidenceContext.Provider>
    );
};

export default InformationRequestStructuredResponsePanel;
