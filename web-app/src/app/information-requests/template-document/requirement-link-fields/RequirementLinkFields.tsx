import {Text} from "@fluentui/react-components";
import {InformationRequestRequirementType} from "../../../models/models.tsx";
import CheckList from "../../shared/check-list/CheckList.tsx";
import {useTemplateDocument} from "../TemplateDocumentContext.ts";
import {requirementLabel} from "../templateDraftRules.ts";
import {RequirementFieldsProps} from "../template-requirement-dialog/requirementDraft.ts";
import {useRequirementLinkFieldsStyles} from "./RequirementLinkFieldsStyles.tsx";

const RequirementLinkFields = ({id, draft, readOnly, onChange}: RequirementFieldsProps) =>
{
    const styles = useRequirementLinkFieldsStyles();
    const {document} = useTemplateDocument();
    const documents = document.sections
        .flatMap(section => section.requirements)
        .filter(requirement => requirement.requirementType === InformationRequestRequirementType.DOCUMENT
            && requirement.requirementKey !== draft.requirementKey)
        .map(requirement => ({value: requirement.requirementKey, label: requirementLabel(requirement)}));
    const isDocument = draft.requirementType === InformationRequestRequirementType.DOCUMENT;

    if (documents.length === 0)
    {
        return (
            <Text id={`${id}-links-empty`}
                  className={styles.muted}>
                Add a Document requirement to link supporting or substitute files.
            </Text>
        );
    }

    return (
        <div id={`${id}-links`}
             className={styles.stack}>
            {!isDocument && (
                <CheckList id={`${id}-supporting-list`}
                           label={"Documents that support this answer"}
                           options={documents}
                           selected={draft.supportingEvidenceRequirementKeys ?? []}
                           disabled={readOnly}
                           onChange={supportingEvidenceRequirementKeys => onChange({supportingEvidenceRequirementKeys})}/>
            )}
            {isDocument && (
                <CheckList id={`${id}-substitute-list`}
                           label={"Documents that can stand in for this one"}
                           options={documents}
                           selected={draft.substituteRequirementKeys ?? []}
                           disabled={readOnly}
                           onChange={substituteRequirementKeys => onChange({substituteRequirementKeys})}/>
            )}
        </div>
    );
};

export default RequirementLinkFields;
