import {Text} from "@fluentui/react-components";
import {WorkflowApplicabilityDraft} from "../../../../models/models.tsx";
import ApplicabilityEditor from "../applicability-editor/ApplicabilityEditor.tsx";
import RequirementConditionEditor from "../requirement-condition-editor/RequirementConditionEditor.tsx";
import {ApplicabilityTriggerKind} from "../workflowApplicability.ts";

interface WorkflowApplicabilityPageProps
{
    scope?: string;
    triggerKind?: ApplicabilityTriggerKind;
    applicability?: WorkflowApplicabilityDraft;
    onChange: (next?: WorkflowApplicabilityDraft) => void;
}

const WorkflowApplicabilityPage = ({scope, triggerKind, applicability, onChange}: WorkflowApplicabilityPageProps) =>
{
    if (scope !== "ORG")
    {
        return (
            <Text id="workflow-applicability-unavailable-message">
                Applicability conditions are available only for organization workflows.
            </Text>
        );
    }
    if (triggerKind === "SUBMISSION_PACKAGE")
    {
        return (
            <RequirementConditionEditor applicability={applicability}
                                        onChange={onChange}/>
        );
    }
    if (triggerKind === "REQUEST") {
        return (
            <Text id="workflow-applicability-request-message">
                Exchange Field conditions apply only to Exchange triggers. This workflow runs for every event of its
                Information Request trigger. Requirement conditions need a trigger that names a Submission Package.
            </Text>
        );
    }
    return (
        <ApplicabilityEditor applicability={applicability}
                             onChange={onChange}/>
    );
};

export default WorkflowApplicabilityPage;
