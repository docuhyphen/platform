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
    accessLinkToken,
    onRefresh,
}: Props) =>
{
    const commands = useMemo(
        () => structuredResponseCommands({accessLinkToken}),
        [accessLinkToken],
    );

    return (
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
    );
};

export default InformationRequestStructuredResponsePanel;
