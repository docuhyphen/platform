import {InformationRequestRequiredness, InformationRequestTemplateRequirementDto} from "../../../models/models.tsx";
import RequirementEvidencePanel
    from "../../requirement-evidence/requirement-evidence-panel/RequirementEvidencePanel.tsx";
import ResponseRequirementHeader from "../response-requirement-header/ResponseRequirementHeader.tsx";

interface Props {
    requestId: string;
    requirement: InformationRequestTemplateRequirementDto;
    requirementId: string;
    elementId: string;
    locked: boolean;
    collecting: boolean;
}

const DocumentRequirementHeading = ({requestId, requirement, requirementId, elementId, locked, collecting}: Props) =>
    collecting
        ? (
            <RequirementEvidencePanel requestId={requestId}
                                      requirementId={requirementId}
                                      prompt={requirement.prompt}
                                      required={requirement.requiredness !== InformationRequestRequiredness.OPTIONAL}
                                      helpText={requirement.helpText}
                                      elementId={elementId}
                                      locked={locked}
                                      evidencePolicy={requirement.evidencePolicy}/>
        )
        : (
            <ResponseRequirementHeader requirement={requirement}
                                       elementId={elementId}/>
        );

export default DocumentRequirementHeading;
