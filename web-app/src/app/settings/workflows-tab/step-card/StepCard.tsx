import {useEffect, useState} from "react";
import {
    Button, Divider, Input, Select, Text,
} from "@fluentui/react-components";
import {
    AssigneeSpecDraft,
    CommunicationSummaryDto,
    QuorumKind,
    StepOutcomeSpecDraft,
    WorkflowStepSpecDraft,
    WorkflowStepType,
    WorkflowSubjectFieldDto,
    WorkflowTriggerEventDto,
} from "../../../models/models.tsx";
import {useStepCardStyles} from "./StepCardStyles.tsx";
import {BackIcon, DeleteIcon} from "../../../components/IconBundles.tsx";
import AssigneeBuilder from "../assignee-builder/AssigneeBuilder.tsx";
import {formatTriggerName} from "../workflowUtils.ts";
import CommunicationPickerDialog from "../../../components/communication-picker/CommunicationPickerDialog.tsx";
import {getCommunication} from "../../../../services/communicationService.ts";
import {DismissCircleRegular} from "@fluentui/react-icons";
import ApprovalSlaFields from "./approval-sla-fields/ApprovalSlaFields.tsx";
import ExtractedConditionExpressionBuilder from "./condition-expression/ConditionExpressionBuilder.tsx";
import StepDeleteDialog from "./step-delete-dialog/StepDeleteDialog.tsx";
import {findRoutesReferencingStep} from "../workflow-designer/stepMutations.ts";

const STEP_TYPE_LABELS: Record<WorkflowStepType, string> = {
    APPROVAL: "Approval",
    NOTIFICATION: "Notification",
    CONDITION: "Condition",
    ACTION: "Action",
    WAIT_FOR_COUNTERPARTY_CLEARANCE: "Wait for Counterparty Clearance",
};

const BUILT_IN_ACTION_KEYS = ["exchange.auto-accept", "exchange.send-reminder", "exchange.revoke-access"];

interface Props
{
    index: number;
    step: WorkflowStepSpecDraft;
    steps: WorkflowStepSpecDraft[];
    onChange: (step: WorkflowStepSpecDraft) => void;
    onRemove: () => void;
    triggers: WorkflowTriggerEventDto[];
    subjectFields: WorkflowSubjectFieldDto[];
    onBack: () => void;
}

const OutcomeField = ({label, value, steps, triggers, onChange}: {
    label: string;
    value?: StepOutcomeSpecDraft;
    steps: WorkflowStepSpecDraft[];
    triggers: WorkflowTriggerEventDto[];
    onChange: (v: StepOutcomeSpecDraft) => void;
}) =>
{
    const styles = useStepCardStyles();
    return (
        <div className={styles.outcomeFieldColumn}>
            <Text size={200} weight="semibold">{label}</Text>
            <div className={styles.outcomeFieldRow}>
                <Select
                    id={`outcome-nextstep-select-${label.replace(/\s+/g, "-").toLowerCase()}`}
                    value={value?.nextStep ?? "END"}
                    onChange={(_, d) => onChange({...value, nextStep: d.value})}
                    size="small"
                >
                    <option value="END">End workflow</option>
                    {steps.map((s, i) => (
                        <option key={i} value={String(i)}>
                            {`Go to step ${i + 1}${s.name ? ` (${s.name})` : ""}`}
                        </option>
                    ))}
                </Select>
                <Select
                    id={`outcome-emit-select-${label.replace(/\s+/g, "-").toLowerCase()}`}
                    value={value?.emit ?? ""}
                    onChange={(_, d) => onChange({
                        ...value,
                        nextStep: value?.nextStep ?? "END",
                        emit: d.value || undefined,
                    })}
                    size="small"
                    className={styles.outcomeEmitSelect}
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
};

const StepCard = ({index, step, steps, onChange, onRemove, triggers, subjectFields, onBack}: Props) =>
{
    const styles = useStepCardStyles();
    const [showDeleteDialog, setShowDeleteDialog] = useState(false);
    const [templatePickerOpen, setTemplatePickerOpen] = useState(false);
    const [selectedCommunicationName, setSelectedCommunicationName] = useState<string | null>(null);

    useEffect(() =>
    {
        if (step.communicationId && step.type === 'NOTIFICATION')
        {
            getCommunication(step.communicationId)
                .then(t => setSelectedCommunicationName(t.name))
                .catch(() => setSelectedCommunicationName(step.communicationId ?? null));
        }
        else
        {
            setSelectedCommunicationName(null);
        }
    }, [step.communicationId, step.type]);

    const affectedRoutes = findRoutesReferencingStep(steps, index);

    const onDeleteConfirm = () =>
    {
        setShowDeleteDialog(false);
        onRemove();
    };

    const patch = (p: Partial<WorkflowStepSpecDraft>) => onChange({...step, ...p});
    const patchAssignees = (assignees: AssigneeSpecDraft[]) => patch({assignees});

    return (
        <div className={styles.card}>
            <div className={styles.cardHeader}>
                <Button
                    id={`step-card-back-btn-${index}`}
                    size="small"
                    appearance="subtle"
                    shape="circular"
                    icon={<BackIcon/>}
                    aria-label="Back to steps"
                    onClick={onBack}
                />
                <Button
                    id={`step-card-delete-btn-${index}`}
                    size="small"
                    appearance="subtle"
                    shape={"circular"}
                    icon={<DeleteIcon/>}
                    onClick={() => setShowDeleteDialog(true)}
                    aria-label="Remove step"
                />
            </div>

            <StepDeleteDialog
                open={showDeleteDialog}
                stepNumber={index + 1}
                stepLabel={step.name || STEP_TYPE_LABELS[step.type]}
                affectedRoutes={affectedRoutes}
                onConfirm={onDeleteConfirm}
                onCancel={() => setShowDeleteDialog(false)}
            />

            <div className={styles.cardBody}>
                    <div className={styles.fieldGroup}>
                        <div className={`${styles.field} ${styles.fullWidth}`}>
                            <Text size={200} weight="semibold">Step Name</Text>
                            <Input
                                id={`step-card-name-input-${index}`}
                                size="small"
                                placeholder="e.g. Manager Approval"
                                value={step.name ?? ""}
                                onChange={(_, d) => patch({name: d.value || undefined})}
                            />
                        </div>

                        <div className={styles.field}>
                            <Text size={200} weight="semibold">Step Type</Text>
                            <Select
                                id={`step-card-type-select-${index}`}
                                value={step.type}
                                onChange={(_, d) => patch({type: d.value as WorkflowStepType})}
                                size="small"
                            >
                                {(Object.keys(STEP_TYPE_LABELS) as WorkflowStepType[]).map(t => (
                                    <option key={t} value={t}>{STEP_TYPE_LABELS[t]}</option>
                                ))}
                            </Select>
                        </div>

                        {step.type === "APPROVAL" && (
                            <div className={styles.field}>
                                <Text size={200} weight="semibold">Quorum</Text>
                                <div className={styles.quorumRow}>
                                    <Select
                                        id={`step-card-quorum-select-${index}`}
                                        value={step.quorum.kind}
                                        onChange={(_, d) => patch({quorum: {kind: d.value as QuorumKind}})}
                                        size="small"
                                    >
                                        <option value="ANY">Any (first to approve)</option>
                                        <option value="ALL">All must approve</option>
                                        <option value="N_OF_M">N of M approvers</option>
                                    </Select>
                                    {step.quorum.kind === "N_OF_M" && (
                                        <Input
                                            id={`step-card-quorum-n-input-${index}`}
                                            size="small"
                                            type="number"
                                            className={styles.quorumNInput}
                                            value={String(step.quorum.n ?? 1)}
                                            onChange={(_, d) => patch({quorum: {...step.quorum, n: parseInt(d.value) || 1}})}
                                        />
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
                                    <Select
                                        id={`step-card-action-select-${index}`}
                                        value={step.actionHandlerKey ?? ""}
                                        onChange={(_, d) => patch({actionHandlerKey: d.value})}
                                        size="small"
                                    >
                                        <option value="">Select action...</option>
                                        {BUILT_IN_ACTION_KEYS.map(k => (
                                            <option key={k} value={k}>{formatTriggerName(k)}</option>
                                        ))}
                                    </Select>
                                ) : (
                                    <div className={styles.notificationRow}>
                                        <Button
                                            id={`step-card-pick-communication-btn-${index}`}
                                            size="small"
                                            appearance="secondary"
                                            shape="circular"
                                            onClick={() => setTemplatePickerOpen(true)}
                                        >
                                            {selectedCommunicationName ?? 'Select communication…'}
                                        </Button>
                                        {step.communicationId && (
                                            <Button
                                                id={`step-card-clear-communication-btn-${index}`}
                                                size="small"
                                                appearance="subtle"
                                                shape="circular"
                                                icon={<DismissCircleRegular/>}
                                                onClick={() => { patch({communicationId: undefined}); setSelectedCommunicationName(null); }}
                                            >
                                                Clear
                                            </Button>
                                        )}
                                        {!step.communicationId && (
                                            <Text
                                                size={200}
                                                className={styles.notificationHint}
                                            >
                                                Leave blank to use the default system notification.
                                            </Text>
                                        )}
                                    </div>
                                )}
                            </div>
                        )}

                        {step.type === "CONDITION" && (
                            <ExtractedConditionExpressionBuilder
                                expression={step.predicateExpression}
                                subjectFields={subjectFields}
                                onChange={expr => patch({predicateExpression: expr})}
                            />
                        )}

                        {step.type === "APPROVAL" && (
                            <ApprovalSlaFields
                                index={index}
                                step={step}
                                subjectFields={subjectFields}
                                onPatch={patch}
                            />
                        )}
                    </div>

                    {(step.type === "APPROVAL" || step.type === "NOTIFICATION") && (
                        <>
                            <Divider/>
                            <AssigneeBuilder
                                label="Assignees"
                                assignees={step.assignees}
                                onChange={patchAssignees}
                                subjectFields={subjectFields}
                            />
                        </>
                    )}

                    {step.type === "WAIT_FOR_COUNTERPARTY_CLEARANCE" && (
                        <>
                            <Divider/>
                            <Text
                                size={200}
                                className={styles.waitDescription}
                            >
                                Pauses this workflow until all workflows on the other party's side of this exchange have completed.
                            </Text>
                            <div className={styles.outcomeRow}>
                                <OutcomeField label="When unblocked" value={step.onApprove} steps={steps}
                                              triggers={triggers} onChange={v => patch({onApprove: v})}/>
                            </div>
                        </>
                    )}

                    {(step.type === "APPROVAL" || step.type === "CONDITION") && <Divider/>}

                    {step.type === "APPROVAL" && (
                        <div className={styles.outcomeRow}>
                            <OutcomeField label="On Approve" value={step.onApprove} steps={steps}
                                          triggers={triggers} onChange={v => patch({onApprove: v})}/>
                            <OutcomeField label="On Reject" value={step.onReject} steps={steps}
                                          triggers={triggers} onChange={v => patch({onReject: v})}/>
                        </div>
                    )}

                    {step.type === "CONDITION" && (
                        <div className={styles.outcomeRow}>
                            <OutcomeField label="If condition is true" value={step.onTrue} steps={steps}
                                          triggers={triggers} onChange={v => patch({onTrue: v})}/>
                            <OutcomeField label="If condition is false" value={step.onFalse} steps={steps}
                                          triggers={triggers} onChange={v => patch({onFalse: v})}/>
                        </div>
                    )}
            </div>

            <CommunicationPickerDialog
                open={templatePickerOpen}
                onClose={() => setTemplatePickerOpen(false)}
                onSelect={(t: CommunicationSummaryDto) =>
                {
                    patch({communicationId: t.id});
                    setSelectedCommunicationName(t.name);
                    setTemplatePickerOpen(false);
                }}
                selectedId={step.communicationId}
            />
        </div>
    );
};

export default StepCard;
