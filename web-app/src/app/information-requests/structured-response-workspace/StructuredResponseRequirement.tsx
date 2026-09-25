import {Text} from "@fluentui/react-components";
import FieldValueEditor from "../../exchanges/components/exchange-fields-tab/FieldValueEditor.tsx";
import {toFieldElementId} from "../../exchanges/components/exchange-fields-tab/fieldLayoutUtils.ts";
import {
    InformationRequestRequirementType,
    InformationRequestResponseDto,
    InformationRequestTemplateRequirementDto,
    SchemaFieldBindingDto,
} from "../../models/models.tsx";
import RequirementEvidencePanel from "../requirement-evidence/requirement-evidence-panel/RequirementEvidencePanel.tsx";
import {useInformationRequestStructuredResponseWorkspaceStyles} from "./InformationRequestStructuredResponseWorkspaceStyles.tsx";
import {
    ResponseEdits,
    responseForFieldRequirement,
    responseKey,
    shownFieldValues,
} from "./structuredResponseWorkspaceState.ts";

interface Props
{
    requestId: string;
    occurrencePath: string;
    requirement: InformationRequestTemplateRequirementDto;
    bindings: SchemaFieldBindingDto[];
    responses: InformationRequestResponseDto[];
    edits: ResponseEdits;
    setEdits: (edits: (previous: ResponseEdits) => ResponseEdits) => void;
}

const StructuredResponseRequirement = ({
    requestId,
    occurrencePath,
    requirement,
    bindings,
    responses,
    edits,
    setEdits,
}: Props) =>
{
    const styles = useInformationRequestStructuredResponseWorkspaceStyles();
    const response = responseForFieldRequirement(responses, requirement, occurrencePath);
    const elementId = toFieldElementId(`${occurrencePath}-${requirement.requirementKey}`);

    if (requirement.requirementType === InformationRequestRequirementType.DOCUMENT)
    {
        if (!response) return null;
        return (
            <div id={`information-request-response-requirement-${elementId}`}
                 className={styles.requirement}>
                <RequirementEvidencePanel requestId={requestId}
                                          requirementId={response.informationRequestRequirementId}
                                          prompt={requirement.prompt}
                                          elementId={elementId}/>
            </div>
        );
    }

    const binding = bindings.find(candidate => candidate.fieldDefinitionId === requirement.collectedFieldDefinitionId);
    if (!binding) return null;
    const key = responseKey(requirement.id, occurrencePath);
    const shown = shownFieldValues(binding, occurrencePath, requirement.id, response, edits);

    return (
        <div id={`information-request-response-requirement-${elementId}`}
             className={styles.requirement}>
            <Text id={`information-request-response-prompt-${elementId}`}
                  className={styles.prompt}>
                {requirement.prompt}
            </Text>
            <FieldValueEditor binding={binding}
                              value={shown[binding.fieldContractId]}
                              onChange={value => setEdits(previous => ({
                                  ...previous,
                                  [key]: {...previous[key], [binding.fieldContractId]: value},
                              }))}
                              showLabel={false}/>
        </div>
    );
};

export default StructuredResponseRequirement;
