import {Divider, Text} from "@fluentui/react-components";
import {AssigneeSpecDraft, WorkflowSubjectFieldDto} from "../../../../../models/models.tsx";
import AssigneeBuilder from "../../../assignee-builder/AssigneeBuilder.tsx";
import {useEscalationTargetsFieldStyles} from "./EscalationTargetsFieldStyles.tsx";

interface EscalationTargetsFieldProps
{
    index: number;
    targets: AssigneeSpecDraft[];
    subjectFields: WorkflowSubjectFieldDto[];
    onChange: (targets: AssigneeSpecDraft[]) => void;
    allowTenantDirectory: boolean;
}

const EscalationTargetsField = ({index, targets, subjectFields, onChange, allowTenantDirectory}: EscalationTargetsFieldProps) =>
{
    const styles = useEscalationTargetsFieldStyles();

    return (
        <>
            <Divider id={"approval-sla-fields-divider-1"}/>
            <div id={`approval-sla-fields-targets-${index}`}
                 className={styles.escalationTargets}>
                <Text id={`approval-sla-fields-targets-hint-${index}`}
                      size={200}
                      className={styles.escalationHint}>
                    Choose the assignees who receive the step after the SLA deadline is missed.
                </Text>
                <AssigneeBuilder label="Escalation targets"
                                 assignees={targets}
                                 onChange={onChange}
                                 subjectFields={subjectFields}
                                 allowTenantDirectory={allowTenantDirectory} />
            </div>
        </>
    );
};

export default EscalationTargetsField;
