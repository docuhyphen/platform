import {useMemo} from "react";
import {
    InformationRequestDto,
    InformationRequestGroupOccurrenceDto,
    InformationRequestResponseDto,
    InformationRequestTemplateConditionRuleDto,
    InformationRequestTemplateGroupDto,
    InformationRequestTemplateRequirementDto,
    SchemaFieldBindingDto,
} from "../../models/models.tsx";
import {RequirementEvidenceContext} from "../requirement-evidence/RequirementEvidenceContext.ts";
import {requirementEvidenceCommands} from "../requirement-evidence/requirementEvidenceCommands.ts";
import {ReusableAnswerContext} from "../reusable-answer/ReusableAnswerContext.ts";
import {useReusableAnswers} from "../reusable-answer/useReusableAnswers.ts";
import InformationRequestStructuredResponseWorkspace from "./InformationRequestStructuredResponseWorkspace.tsx";
import {structuredResponseCommands} from "./structuredResponseCommands.ts";

interface Props
{
    request: InformationRequestDto;
    responseETag: string;
    groups: InformationRequestTemplateGroupDto[];
    conditionRules: InformationRequestTemplateConditionRuleDto[];
    occurrences: InformationRequestGroupOccurrenceDto[];
    requirements: InformationRequestTemplateRequirementDto[];
    bindings: SchemaFieldBindingDto[];
    responses: InformationRequestResponseDto[];
    evidenceUploadAvailable: boolean;
    evidenceMalwareScanning: boolean;
    accessLinkToken?: string;
    onRefresh: () => void;
}

const InformationRequestStructuredResponsePanel = ({
    request,
    responseETag,
    groups,
    conditionRules,
    occurrences,
    requirements,
    bindings,
    responses,
    evidenceUploadAvailable,
    evidenceMalwareScanning,
    accessLinkToken,
    onRefresh,
}: Props) =>
{
    const commands = useMemo(
        () => structuredResponseCommands({accessLinkToken}),
        [accessLinkToken],
    );
    const evidence = useMemo(
        () => ({
            uploadAvailable: evidenceUploadAvailable,
            malwareScanning: evidenceMalwareScanning,
            commands: requirementEvidenceCommands({accessLinkToken}),
        }),
        [accessLinkToken, evidenceMalwareScanning, evidenceUploadAvailable],
    );
    const reusableAnswers = useReusableAnswers(request.id, responseETag, accessLinkToken);

    return (
        <RequirementEvidenceContext.Provider value={evidence}>
            <ReusableAnswerContext.Provider value={reusableAnswers}>
                <InformationRequestStructuredResponseWorkspace request={request}
                                                               responseETag={responseETag}
                                                               enabled={true}
                                                               groups={groups}
                                                               conditionRules={conditionRules}
                                                               occurrences={occurrences}
                                                               requirements={requirements}
                                                               bindings={bindings}
                                                               responses={responses}
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
