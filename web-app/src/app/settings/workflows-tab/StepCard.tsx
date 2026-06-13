import React from "react";
import {Badge, Button, Input, Select, Text} from "@fluentui/react-components";
import {
    AssigneeSpecDraft,
    EscalationAction,
    QuorumKind,
    StepOutcomeSpecDraft,
    WorkflowStepSpecDraft,
    WorkflowStepType,
    WorkflowSubjectFieldDto,
    WorkflowTriggerEventDto,
} from "../../models/models.tsx";
import {useStepCardStyles} from "./StepCardStyles.tsx";
import {DeleteIcon, ToggleHeaderDownIcon, ToggleHeaderUpIcon} from "../../components/IconBundles.tsx";
import AssigneeBuilder from "./AssigneeBuilder.tsx";
import {formatTriggerName} from "./workflowUtils.ts";

const STEP_TYPE_LABELS: Record<WorkflowStepType, string> = {
    APPROVAL: "Approval", NOTIFICATION: "Notification", CONDITION: "Condition", ACTION: "Action",
};

const BUILT_IN_ACTION_KEYS = ["exchange.auto-accept", "exchange.send-reminder", "exchange.revoke-access"];

interface Props
{
    index: number;
    step: WorkflowStepSpecDraft;
    stepCount: number;
    onChange: (step: WorkflowStepSpecDraft) => void;
    onRemove: () => void;
    triggers: WorkflowTriggerEventDto[];
    subjectFields: WorkflowSubjectFieldDto[];
}

const OutcomeField = ({label, value, stepCount, triggers, onChange}: {
    label: string;
    value?: StepOutcomeSpecDraft;
    stepCount: number;
    triggers: WorkflowTriggerEventDto[];
    onChange: (v: StepOutcomeSpecDraft) => void;
}) => (
    <div style={{display: "flex", flexDirection: "column", gap: "4px"}}>
        <Text size={200} weight="semibold">{label}</Text>
        <div style={{display: "flex", gap: "4px", flexWrap: "wrap"}}>
            <Select
                value={value?.nextStep ?? "END"}
                onChange={(_, d) => onChange({...value, nextStep: d.value})}
                size="small"
            >
                <option value="END">End workflow</option>
                {Array.from({length: stepCount}, (_, i) => (
                    <option key={i} value={String(i)}>Go to step {i + 1}</option>
                ))}
            </Select>
            <Select
                value={value?.emit ?? ""}
                onChange={(_, d) => onChange({
                    ...value,
                    nextStep: value?.nextStep ?? "END",
                    emit: d.value || undefined,
                })}
                size="small"
                style={{flex: 1, minWidth: "10rem"}}
            >
                <option value="">No event emitted</option>
                {triggers.map(t => (
                    <option key={t.eventName} value={t.eventName}>
                        {formatTriggerName(t.eventName)}
                    </option>
                ))}
            </Select>
        </div>
    </div>
);

const StepCard = ({index, step, stepCount, onChange, onRemove, triggers, subjectFields}: Props) =>
{
    const styles = useStepCardStyles();
    const [expanded, setExpanded] = React.useState(true);

    const patch = (p: Partial<WorkflowStepSpecDraft>) => onChange({...step, ...p});
    const patchAssignees = (assignees: AssigneeSpecDraft[]) => patch({assignees});

    return (
        <div className={styles.card}>
            <div className={styles.cardHeader} onClick={() => setExpanded(e => !e)}>
                <Text className={styles.stepNumber} size={200}>Step {index + 1}</Text>
                <Badge appearance="outline" color="informative" className={styles.headerTitle}>
                    {STEP_TYPE_LABELS[step.type]}
                </Badge>
                {expanded ? <ToggleHeaderUpIcon/> : <ToggleHeaderDownIcon/>}
                <Button size="small" appearance="subtle" icon={<DeleteIcon/>}
                        onClick={(e) => { e.stopPropagation(); onRemove(); }}
                        aria-label="Remove step"/>
            </div>

            {expanded && (
                <div className={styles.cardBody}>
                    <div className={styles.fieldGroup}>
                        <div className={styles.field}>
                            <Text size={200} weight="semibold">Step Type</Text>
                            <Select value={step.type}
                                    onChange={(_, d) => patch({type: d.value as WorkflowStepType})} size="small">
                                {(Object.keys(STEP_TYPE_LABELS) as WorkflowStepType[]).map(t => (
                                    <option key={t} value={t}>{STEP_TYPE_LABELS[t]}</option>
                                ))}
                            </Select>
                        </div>

                        {step.type === "APPROVAL" && (
                            <div className={styles.field}>
                                <Text size={200} weight="semibold">Quorum</Text>
                                <div style={{display: "flex", gap: "4px"}}>
                                    <Select value={step.quorum.kind}
                                            onChange={(_, d) => patch({quorum: {kind: d.value as QuorumKind}})}
                                            size="small">
                                        <option value="ANY">Any (first to approve)</option>
                                        <option value="ALL">All must approve</option>
                                        <option value="N_OF_M">N of M approvers</option>
                                    </Select>
                                    {step.quorum.kind === "N_OF_M" && (
                                        <Input size="small" type="number" style={{width: "4rem"}}
                                               value={String(step.quorum.n ?? 1)}
                                               onChange={(_, d) => patch({quorum: {...step.quorum, n: parseInt(d.value) || 1}})}/>
                                    )}
                                </div>
                            </div>
                        )}

                        {(step.type === "NOTIFICATION" || step.type === "ACTION") && (
                            <div className={styles.field}>
                                <Text size={200} weight="semibold">
                                    {step.type === "ACTION" ? "Action" : "Message Template"}
                                </Text>
                                {step.type === "ACTION" ? (
                                    <Select value={step.actionHandlerKey ?? ""}
                                            onChange={(_, d) => patch({actionHandlerKey: d.value})} size="small">
                                        <option value="">Select action...</option>
                                        {BUILT_IN_ACTION_KEYS.map(k => (
                                            <option key={k} value={k}>{formatTriggerName(k)}</option>
                                        ))}
                                    </Select>
                                ) : (
                                    <Input size="small" placeholder="e.g. exchange.reminder"
                                           value={step.messageTemplateKey ?? ""}
                                           onChange={(_, d) => patch({messageTemplateKey: d.value || undefined})}/>
                                )}
                            </div>
                        )}

                        {step.type === "CONDITION" && (
                            <div className={`${styles.field} ${styles.fullWidth}`}>
                                <Text size={200} weight="semibold">Condition Expression</Text>
                                <Input size="small"
                                       placeholder='e.g. $subject.recipientType == &apos;GROUP&apos;'
                                       value={step.predicateExpression ?? ""}
                                       onChange={(_, d) => patch({predicateExpression: d.value || undefined})}/>
                            </div>
                        )}

                        <div className={styles.field}>
                            <Text size={200} weight="semibold">SLA (minutes)</Text>
                            <Input size="small" type="number" placeholder="No deadline"
                                   value={step.slaMinutes !== undefined ? String(step.slaMinutes) : ""}
                                   onChange={(_, d) => patch({slaMinutes: d.value ? parseInt(d.value) : undefined})}/>
                        </div>

                        {step.slaMinutes && (
                            <div className={styles.field}>
                                <Text size={200} weight="semibold">When deadline is missed</Text>
                                <Select
                                    value={step.escalation?.afterSlaBreach ?? "AUTO_REJECT"}
                                    onChange={(_, d) => patch({escalation: {
                                        afterSlaBreach: d.value as EscalationAction,
                                        escalateTo: step.escalation?.escalateTo ?? [],
                                    }})}
                                    size="small">
                                    <option value="AUTO_REJECT">Reject automatically</option>
                                    <option value="AUTO_APPROVE">Approve automatically</option>
                                    <option value="ESCALATE">Escalate to someone else</option>
                                </Select>
                            </div>
                        )}
                    </div>

                    <AssigneeBuilder
                        label="Assignees"
                        assignees={step.assignees}
                        onChange={patchAssignees}
                        subjectFields={subjectFields}
                    />

                    {step.type !== "CONDITION" ? (
                        <div className={styles.outcomeRow}>
                            <OutcomeField label="On Approve" value={step.onApprove} stepCount={stepCount}
                                          triggers={triggers} onChange={v => patch({onApprove: v})}/>
                            {step.type === "APPROVAL" && (
                                <OutcomeField label="On Reject" value={step.onReject} stepCount={stepCount}
                                              triggers={triggers} onChange={v => patch({onReject: v})}/>
                            )}
                        </div>
                    ) : (
                        <div className={styles.outcomeRow}>
                            <OutcomeField label="If condition is true" value={step.onTrue} stepCount={stepCount}
                                          triggers={triggers} onChange={v => patch({onTrue: v})}/>
                            <OutcomeField label="If condition is false" value={step.onFalse} stepCount={stepCount}
                                          triggers={triggers} onChange={v => patch({onFalse: v})}/>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
};

export default StepCard;
