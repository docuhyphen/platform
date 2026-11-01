import {Input, Select, Text} from "@fluentui/react-components";
import {EscalationAction, WorkflowStepSpecDraft, WorkflowSubjectFieldDto} from "../../../../models/models.tsx";
import {useApprovalSlaFieldsStyles} from "./ApprovalSlaFieldsStyles.tsx";
import {useApprovalSlaPatches} from "./useApprovalSlaPatches.ts";
import EscalationTargetsField from "./escalation-targets-field/EscalationTargetsField.tsx";

interface Props
{
    index: number;
    step: WorkflowStepSpecDraft;
    subjectFields: WorkflowSubjectFieldDto[];
    onPatch: (patch: Partial<WorkflowStepSpecDraft>) => void;
    allowTenantDirectory?: boolean;
}

const ApprovalSlaFields = ({
    index,
    step,
    subjectFields,
    onPatch,
    allowTenantDirectory = true,
}: Props) =>
{
    const styles = useApprovalSlaFieldsStyles();
    const {escalationAction, patchSlaMinutes, patchEscalationAction, patchEscalationTargets} = useApprovalSlaPatches(step, onPatch);

    return (
        <>
            <div id={`approval-sla-fields-sla-${index}`}
                 className={styles.field}>
                <Text id={`approval-sla-fields-sla-label-${index}`}
                      size={200}
                      weight="semibold">
                    SLA (minutes)
                </Text>
                <Input id={`approval-sla-fields-sla-input-${index}`}
                       size="small"
                       type="number"
                       placeholder="No deadline"
                       value={step.slaMinutes !== undefined ? String(step.slaMinutes) : ""}
                       onChange={(_, d) => patchSlaMinutes(d.value)} />
            </div>

            {step.slaMinutes !== undefined && (
                <>
                    <div id={`approval-sla-fields-escalation-${index}`}
                         className={styles.field}>
                        <Text id={`approval-sla-fields-escalation-label-${index}`}
                              size={200}
                              weight="semibold">
                            When deadline is missed
                        </Text>
                        <Select id={`approval-sla-fields-escalation-select-${index}`}
                                value={escalationAction}
                                onChange={(_, d) => patchEscalationAction(d.value as EscalationAction)}
                                size="small">
                            <option id={"approval-sla-fields-option-1"}
                                    value="AUTO_REJECT">Reject automatically</option>
                            <option id={"approval-sla-fields-option-2"}
                                    value="AUTO_APPROVE">Approve automatically</option>
                            <option id={"approval-sla-fields-option-3"}
                                    value="ESCALATE">Escalate to someone else</option>
                        </Select>
                    </div>

                    {escalationAction === "ESCALATE" && (
                        <EscalationTargetsField index={index}
                                                targets={step.escalation?.escalateTo ?? []}
                                                subjectFields={subjectFields}
                                                onChange={patchEscalationTargets}
                                                allowTenantDirectory={allowTenantDirectory} />
                    )}
                </>
            )}
        </>
    );
};

export default ApprovalSlaFields;
