import {Text} from "@fluentui/react-components";
import {WorkflowApplicabilityDraft} from "../../../../models/models.tsx";
import ApplicabilityEditor from "../applicability-editor/ApplicabilityEditor.tsx";

interface WorkflowApplicabilityPageProps
{
    scope?: string;
    requestTrigger: boolean;
    applicability?: WorkflowApplicabilityDraft;
    onChange: (next?: WorkflowApplicabilityDraft) => void;
}

const WorkflowApplicabilityPage = ({scope, requestTrigger, applicability, onChange}: WorkflowApplicabilityPageProps) =>
{
    if (scope !== "ORG")
    {
        return (
            <Text id="workflow-applicability-unavailable-message">
                Applicability conditions are available only for organization workflows.
            </Text>
        );
    }
    if (requestTrigger)
    {
        const requirementCount = applicability?.requirementConditions?.length ?? 0;
        return (
            <Text id="workflow-applicability-request-message">
                {requirementCount === 0
                    ? "Exchange Field conditions apply only to Exchange triggers. This workflow runs for every event of its Information Request trigger."
                    : `Exchange Field conditions apply only to Exchange triggers. This workflow runs only for Submission Packages that meet its ${requirementCount} requirement conditions.`}
            </Text>
        );
    }
    return (
        <ApplicabilityEditor applicability={applicability}
                             onChange={onChange}/>
    );
};

export default WorkflowApplicabilityPage;
