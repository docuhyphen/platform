import {useEffect} from "react";
import {Divider, Input, Select, Text} from "@fluentui/react-components";
import {
    AssigneeSpecDraft,
    EscalationAction,
    EscalationSpecDraft,
    WorkflowStepSpecDraft,
    WorkflowSubjectFieldDto,
} from "../../../../models/models.tsx";
import AssigneeBuilder from "../../assignee-builder/AssigneeBuilder.tsx";
import {useApprovalSlaFieldsStyles} from "./ApprovalSlaFieldsStyles.tsx";

interface Props
{
    index: number;
    step: WorkflowStepSpecDraft;
    subjectFields: WorkflowSubjectFieldDto[];
    onPatch: (patch: Partial<WorkflowStepSpecDraft>) => void;
}

const DEFAULT_ESCALATION: EscalationSpecDraft = {
    afterSlaBreach: "AUTO_REJECT",
    escalateTo: [],
};

const ApprovalSlaFields = ({index, step, subjectFields, onPatch}: Props) =>
{
    const styles = useApprovalSlaFieldsStyles();
    const escalationAction = step.escalation?.afterSlaBreach ?? "AUTO_REJECT";

    useEffect(() =>
    {
        if (step.slaMinutes !== undefined && !step.escalation)
        {
            onPatch({escalation: DEFAULT_ESCALATION});
        }
    }, [onPatch, step.escalation, step.slaMinutes]);

    const patchSlaMinutes = (value: string) =>
    {
        const parsedSlaMinutes = value ? parseInt(value, 10) : undefined;
        const slaMinutes = parsedSlaMinutes !== undefined && Number.isFinite(parsedSlaMinutes)
            ? parsedSlaMinutes
            : undefined;
        onPatch({
            slaMinutes,
            escalation: slaMinutes !== undefined ? step.escalation ?? DEFAULT_ESCALATION : undefined,
        });
    };

    const patchEscalationAction = (afterSlaBreach: EscalationAction) =>
    {
        onPatch({
            escalation: {
                afterSlaBreach,
                escalateTo: step.escalation?.escalateTo ?? [],
            },
        });
    };

    const patchEscalationTargets = (escalateTo: AssigneeSpecDraft[]) =>
    {
        onPatch({
            escalation: {
                afterSlaBreach: escalationAction,
                escalateTo,
            },
        });
    };

    return (
        <>
            <div
                id={`approval-sla-fields-sla-${index}`}
                className={styles.field}
            >
                <Text
                    id={`approval-sla-fields-sla-label-${index}`}
                    size={200}
                    weight="semibold"
                >
                    SLA (minutes)
                </Text>
                <Input
                    id={`approval-sla-fields-sla-input-${index}`}
                    size="small"
                    type="number"
                    placeholder="No deadline"
                    value={step.slaMinutes !== undefined ? String(step.slaMinutes) : ""}
                    onChange={(_, d) => patchSlaMinutes(d.value)}
                />
            </div>

            {step.slaMinutes !== undefined && (
                <>
                    <div
                        id={`approval-sla-fields-escalation-${index}`}
                        className={styles.field}
                    >
                        <Text
                            id={`approval-sla-fields-escalation-label-${index}`}
                            size={200}
                            weight="semibold"
                        >
                            When deadline is missed
                        </Text>
                        <Select
                            id={`approval-sla-fields-escalation-select-${index}`}
                            value={escalationAction}
                            onChange={(_, d) => patchEscalationAction(d.value as EscalationAction)}
                            size="small"
                        >
                            <option value="AUTO_REJECT">Reject automatically</option>
                            <option value="AUTO_APPROVE">Approve automatically</option>
                            <option value="ESCALATE">Escalate to someone else</option>
                        </Select>
                    </div>

                    {escalationAction === "ESCALATE" && (
                        <>
                            <Divider/>
                            <div
                                id={`approval-sla-fields-targets-${index}`}
                                className={styles.escalationTargets}
                            >
                                <Text
                                    id={`approval-sla-fields-targets-hint-${index}`}
                                    size={200}
                                    className={styles.escalationHint}
                                >
                                    Choose the assignees who receive the step after the SLA deadline is missed.
                                </Text>
                                <AssigneeBuilder
                                    label="Escalation targets"
                                    assignees={step.escalation?.escalateTo ?? []}
                                    onChange={patchEscalationTargets}
                                    subjectFields={subjectFields}
                                />
                            </div>
                        </>
                    )}
                </>
            )}
        </>
    );
};

export default ApprovalSlaFields;
