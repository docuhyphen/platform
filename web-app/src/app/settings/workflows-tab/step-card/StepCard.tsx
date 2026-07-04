import React, {useCallback, useEffect, useRef, useState} from "react";
import {
    Badge, Button, Combobox, Dialog, DialogActions, DialogBody, DialogContent, DialogSurface,
    DialogTitle, Divider, Input, Option, Select, Text,
} from "@fluentui/react-components";
import {
    AssigneeSpecDraft,
    EscalationAction,
    CommunicationSummaryDto,
    QuorumKind,
    StepOutcomeSpecDraft,
    WorkflowEntityRefDto,
    WorkflowStepSpecDraft,
    WorkflowStepType,
    WorkflowSubjectFieldDto,
    WorkflowTriggerEventDto,
} from "../../../models/models.tsx";
import {lookupWorkflowEntities} from "../../../../services/workflowService.ts";
import {useStepCardStyles} from "./StepCardStyles.tsx";
import {DeleteIcon, ToggleHeaderDownIcon, ToggleHeaderUpIcon} from "../../../components/IconBundles.tsx";
import AssigneeBuilder from "../assignee-builder/AssigneeBuilder.tsx";
import {formatTriggerName} from "../workflowUtils.ts";
import CommunicationPickerDialog from "../../../components/communication-picker/CommunicationPickerDialog.tsx";
import {getCommunication} from "../../../../services/communicationService.ts";

const STEP_TYPE_LABELS: Record<WorkflowStepType, string> = {
    APPROVAL: "Approval",
    NOTIFICATION: "Notification",
    CONDITION: "Condition",
    ACTION: "Action",
    WAIT_FOR_COUNTERPARTY_CLEARANCE: "Wait for Counterparty Clearance",
};

const CONDITION_OPERATORS = [
    {value: "==",  label: "is equal to"},
    {value: "!=",  label: "is not equal to"},
    {value: ">",   label: "is greater than"},
    {value: "<",   label: "is less than"},
    {value: ">=",  label: "is greater than or equal to"},
    {value: "<=",  label: "is less than or equal to"},
];

function parseConditionExpression(expr: string)
{
    const match = expr.trim().match(/^\$subject\.(\w+)\s*(==|!=|>=|<=|>|<)\s*(.+)$/);
    if (!match) return {field: "", operator: "==", value: ""};
    const raw = match[3].trim();
    const value = raw.startsWith("'") && raw.endsWith("'") ? raw.slice(1, -1) : raw;
    return {field: match[1], operator: match[2], value};
}

function humanizeFieldName(name: string): string
{
    return name
        .replace(/Id$/, "")
        .replace(/([A-Z])/g, " $1")
        .replace(/^./, s => s.toUpperCase())
        .trim();
}

function humanizeEnumValue(val: string): string
{
    return val.trim().split("_")
        .map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
        .join(" ");
}

/** Returns the pipe-separated enum values if the description is an enum list, otherwise null. */
function parseEnumDescription(description: string | undefined): string[] | null
{
    if (!description) return null;
    const parts = description.split("|").map(s => s.trim());
    return parts.length > 1 && parts.every(p => /^[A-Z][A-Z0-9_]*$/.test(p)) ? parts : null;
}

function buildConditionExpression(field: string, operator: string, value: string, quoted: boolean): string | undefined
{
    if (!field || value === "") return undefined;
    const rhs = quoted ? `'${value}'` : value;
    return `$subject.${field} ${operator} ${rhs}`;
}

const EntityPickerCombobox = ({value, lookupType, onSelect}: {
    value: string;
    lookupType: string;
    onSelect: (id: string, label: string) => void;
}) =>
{
    const [inputText, setInputText] = useState("");
    const [results, setResults] = useState<WorkflowEntityRefDto[]>([]);
    const [loading, setLoading] = useState(false);
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    const search = useCallback((q: string) =>
    {
        if (debounceRef.current) clearTimeout(debounceRef.current);
        if (!q.trim()) { setResults([]); return; }
        debounceRef.current = setTimeout(async () =>
        {
            setLoading(true);
            try { setResults(await lookupWorkflowEntities(lookupType, q)); }
            catch { setResults([]); }
            finally { setLoading(false); }
        }, 300);
    }, [lookupType]);

    useEffect(() => () => { if (debounceRef.current) clearTimeout(debounceRef.current); }, []);

    const styles = useStepCardStyles();
    return (
        <Combobox
            id={`entity-picker-combobox-${lookupType}`}
            size="small"
            className={styles.entityPickerCombobox}
            freeform={false}
            placeholder="Search…"
            value={inputText}
            selectedOptions={value ? [value] : []}
            onInput={(e) =>
            {
                const text = (e.target as HTMLInputElement).value;
                setInputText(text);
                search(text);
            }}
            onBlur={() => { if (!inputText) setResults([]); }}
            onOptionSelect={(_, d) =>
            {
                const entity = results.find(r => r.id === d.optionValue);
                if (!entity) return;
                setInputText(entity.label);
                setResults([]);
                onSelect(entity.id, entity.label);
            }}
        >
            {loading ? (
                <Option key="__loading" value="" disabled>Searching…</Option>
            ) : results.length === 0 && inputText.trim() ? (
                <Option key="__empty" value="" disabled>No results</Option>
            ) : (
                results.map(r => (
                    <Option key={r.id} value={r.id}>
                        {r.label}
                        {r.sublabel && (
                            <span className={styles.sublabel}>
                                {r.sublabel}
                            </span>
                        )}
                    </Option>
                ))
            )}
        </Combobox>
    );
};

function getAllowedOperators(f: WorkflowSubjectFieldDto | undefined)
{
    if (!f) return CONDITION_OPERATORS;
    const isEnum = parseEnumDescription(f.description) !== null;
    const identityOnly = f.type === "UUID" || f.type === "STRING" || isEnum || f.type === "boolean";
    return identityOnly
        ? CONDITION_OPERATORS.filter(op => op.value === "==" || op.value === "!=")
        : CONDITION_OPERATORS;
}

const ConditionExpressionBuilder = ({expression, subjectFields, onChange}: {
    expression: string | undefined;
    subjectFields: WorkflowSubjectFieldDto[];
    onChange: (expr: string | undefined) => void;
}) =>
{
    const styles = useStepCardStyles();
    const init = parseConditionExpression(expression ?? "");
    const [field, setField] = useState(init.field);
    const [operator, setOperator] = useState(init.operator);
    const [value, setValue] = useState(init.value);
    const [valueLabel, setValueLabel] = useState(init.value);
    const [fieldSearch, setFieldSearch] = useState(init.field ? humanizeFieldName(init.field) : "");

    const selectedField = subjectFields.find(f => f.name === field);
    const isNumeric = selectedField?.type === "number" || selectedField?.type === "integer";
    const isBoolean = selectedField?.type === "boolean";
    const enumValues = parseEnumDescription(selectedField?.description);
    const quoted = !isNumeric && !isBoolean;
    const lookupType = selectedField?.type === "UUID" ? selectedField.lookupType : undefined;
    const allowedOperators = getAllowedOperators(selectedField);

    const emit = (f: string, op: string, v: string, q: boolean) =>
        onChange(buildConditionExpression(f, op, v, q));

    const filteredFields = fieldSearch && fieldSearch !== (field ? humanizeFieldName(field) : "")
        ? subjectFields.filter(f => humanizeFieldName(f.name).toLowerCase().includes(fieldSearch.toLowerCase()))
        : subjectFields;

    if (subjectFields.length === 0)
    {
        return (
            <div className={`${styles.field} ${styles.fullWidth}`}>
                <Text size={200} weight="semibold">Condition Expression</Text>
                <Input
                    id="condition-expression-input"
                    size="small"
                    placeholder="$subject.fieldName == 'value'"
                    value={expression ?? ""}
                    onChange={(_, d) => onChange(d.value || undefined)}
                />
                <Text
                    size={100}
                    className={styles.conditionHint}
                >
                    Select a trigger event to enable the expression builder.
                </Text>
            </div>
        );
    }

    return (
        <div className={`${styles.field} ${styles.fullWidth}`}>
            <Text size={200} weight="semibold">Condition</Text>
            <div className={styles.conditionRow}>
                <Combobox
                    id="condition-field-combobox"
                    size="small"
                    className={styles.conditionFieldCombobox}
                    freeform={false}
                    placeholder="Search fields..."
                    value={fieldSearch}
                    selectedOptions={field ? [field] : []}
                    onInput={(e) => setFieldSearch((e.target as HTMLInputElement).value)}
                    onBlur={() => setFieldSearch(field ? humanizeFieldName(field) : "")}
                    onOptionSelect={(_, d) =>
                    {
                        const newField = d.optionValue as string;
                        const next = subjectFields.find(f => f.name === newField);
                        const nextAllowed = getAllowedOperators(next);
                        const nextOperator = nextAllowed.find(op => op.value === operator)
                            ? operator
                            : nextAllowed[0].value;
                        const nextNumeric = next?.type === "number" || next?.type === "integer";
                        const nextBoolean = next?.type === "boolean";
                        setField(newField);
                        setFieldSearch(humanizeFieldName(newField));
                        setOperator(nextOperator);
                        setValue("");
                        setValueLabel("");
                        emit(newField, nextOperator, "", !nextNumeric && !nextBoolean);
                    }}
                >
                    {filteredFields.map(f => (
                        <Option key={f.name} value={f.name}>{humanizeFieldName(f.name)}</Option>
                    ))}
                </Combobox>

                <Select
                    id="condition-operator-select"
                    size="small"
                    className={styles.conditionOperatorSelect}
                    value={operator}
                    onChange={(_, d) =>
                    {
                        setOperator(d.value);
                        emit(field, d.value, value, quoted);
                    }}
                >
                    {allowedOperators.map(op => (
                        <option key={op.value} value={op.value}>{op.label}</option>
                    ))}
                </Select>

                {lookupType ? (
                    <EntityPickerCombobox
                        key={field}
                        value={value}
                        lookupType={lookupType}
                        onSelect={(id, label) =>
                        {
                            setValue(id);
                            setValueLabel(label);
                            emit(field, operator, id, true);
                        }}
                    />
                ) : enumValues ? (
                    <Select
                        id="condition-enum-value-select"
                        size="small"
                        className={styles.conditionValueSelect}
                        value={value}
                        onChange={(_, d) =>
                        {
                            setValue(d.value);
                            setValueLabel(humanizeEnumValue(d.value));
                            emit(field, operator, d.value, quoted);
                        }}
                    >
                        <option value="">Select…</option>
                        {enumValues.map(v => (
                            <option key={v} value={v}>{humanizeEnumValue(v)}</option>
                        ))}
                    </Select>
                ) : isBoolean ? (
                    <Select
                        id="condition-bool-value-select"
                        size="small"
                        className={styles.conditionValueSelect}
                        value={value}
                        onChange={(_, d) =>
                        {
                            setValue(d.value);
                            setValueLabel(d.value === "true" ? "Yes" : "No");
                            emit(field, operator, d.value, false);
                        }}
                    >
                        <option value="">Select…</option>
                        <option value="true">Yes</option>
                        <option value="false">No</option>
                    </Select>
                ) : (
                    <Input
                        id="condition-value-input"
                        size="small"
                        className={styles.conditionValueInput}
                        type={isNumeric ? "number" : "text"}
                        placeholder={isNumeric ? "e.g. 5" : "Value…"}
                        value={value}
                        onChange={(_, d) =>
                        {
                            setValue(d.value);
                            setValueLabel(d.value);
                            emit(field, operator, d.value, quoted);
                        }}
                    />
                )}
            </div>
            {field && value && valueLabel && (
                <Text
                    size={100}
                    className={styles.conditionPreview}
                >
                    {humanizeFieldName(field)} {allowedOperators.find(op => op.value === operator)?.label ?? operator} {valueLabel}
                </Text>
            )}
        </div>
    );
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

const DELETE_COUNTDOWN = 5;

const StepCard = ({index, step, steps, onChange, onRemove, triggers, subjectFields}: Props) =>
{
    const styles = useStepCardStyles();
    const [expanded, setExpanded] = React.useState(true);
    const [showDeleteDialog, setShowDeleteDialog] = useState(false);
    const [deleteStarted, setDeleteStarted] = useState(false);
    const [countdown, setCountdown] = useState(DELETE_COUNTDOWN);
    const timerRef = React.useRef<ReturnType<typeof setInterval> | null>(null);
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

    const onDeleteConfirm = () =>
    {
        setDeleteStarted(true);
        setCountdown(DELETE_COUNTDOWN);
        timerRef.current = setInterval(() =>
        {
            setCountdown(c =>
            {
                if (c <= 1)
                {
                    clearInterval(timerRef.current!);
                    setShowDeleteDialog(false);
                    setDeleteStarted(false);
                    onRemove();
                    return 0;
                }
                return c - 1;
            });
        }, 1000);
    };

    const onDeleteCancel = () =>
    {
        if (timerRef.current) clearInterval(timerRef.current);
        setDeleteStarted(false);
        setCountdown(DELETE_COUNTDOWN);
    };

    const onDialogDismiss = () =>
    {
        if (timerRef.current) clearInterval(timerRef.current);
        setDeleteStarted(false);
        setCountdown(DELETE_COUNTDOWN);
        setShowDeleteDialog(false);
    };

    useEffect(() => () =>
    {
        if (timerRef.current) clearInterval(timerRef.current);
    }, []);

    const patch = (p: Partial<WorkflowStepSpecDraft>) => onChange({...step, ...p});
    const patchAssignees = (assignees: AssigneeSpecDraft[]) => patch({assignees});

    return (
        <div className={styles.card}>
            <div className={styles.cardHeader} onClick={() => setExpanded(e => !e)}>
                <Text className={styles.stepNumber} size={200}>
                    Step {index + 1} ({step.name || STEP_TYPE_LABELS[step.type]})
                </Text>
                <Badge appearance="outline" color="informative" className={styles.headerTitle}>
                    {STEP_TYPE_LABELS[step.type]}
                </Badge>
                {expanded ? <ToggleHeaderUpIcon/> : <ToggleHeaderDownIcon/>}
                <Button
                    id={`step-card-delete-btn-${index}`}
                    size="small"
                    appearance="subtle"
                    shape={"circular"}
                    icon={<DeleteIcon/>}
                    onClick={(e) => { e.stopPropagation(); setShowDeleteDialog(true); }}
                    aria-label="Remove step"
                />
            </div>

            <Dialog modalType="alert" open={showDeleteDialog}>
                <DialogSurface>
                    <DialogBody>
                        <DialogTitle>
                            Delete Step {index + 1}: {step.name || STEP_TYPE_LABELS[step.type]}
                        </DialogTitle>
                        <DialogContent>
                            {deleteStarted ? (
                                `Deleting in ${countdown} second${countdown !== 1 ? "s" : ""}…`
                            ) : (
                                "Are you sure? This will permanently remove the step and any routing logic that references it."
                            )}
                        </DialogContent>
                        <DialogActions>
                            {deleteStarted ? (
                                <Button
                                    id={`step-card-cancel-delete-btn-${index}`}
                                    appearance="primary"
                                    shape="circular"
                                    onClick={onDeleteCancel}
                                >
                                    Cancel
                                </Button>
                            ) : (
                                <>
                                    <Button
                                        id={`step-card-confirm-delete-btn-${index}`}
                                        appearance="primary"
                                        shape="circular"
                                        onClick={onDeleteConfirm}
                                    >
                                        Yes, Delete
                                    </Button>
                                    <Button
                                        id={`step-card-dismiss-delete-btn-${index}`}
                                        appearance="secondary"
                                        shape="circular"
                                        onClick={onDialogDismiss}
                                    >
                                        No, Cancel
                                    </Button>
                                </>
                            )}
                        </DialogActions>
                    </DialogBody>
                </DialogSurface>
            </Dialog>

            {expanded && (
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
                            <ConditionExpressionBuilder
                                expression={step.predicateExpression}
                                subjectFields={subjectFields}
                                onChange={expr => patch({predicateExpression: expr})}
                            />
                        )}

                        {step.type === "APPROVAL" && (
                            <>
                                <div className={styles.field}>
                                    <Text size={200} weight="semibold">SLA (minutes)</Text>
                                    <Input
                                        id={`step-card-sla-input-${index}`}
                                        size="small"
                                        type="number"
                                        placeholder="No deadline"
                                        value={step.slaMinutes !== undefined ? String(step.slaMinutes) : ""}
                                        onChange={(_, d) => patch({slaMinutes: d.value ? parseInt(d.value) : undefined})}
                                    />
                                </div>

                                {step.slaMinutes && (
                                    <div className={styles.field}>
                                        <Text size={200} weight="semibold">When deadline is missed</Text>
                                        <Select
                                            id={`step-card-escalation-select-${index}`}
                                            value={step.escalation?.afterSlaBreach ?? "AUTO_REJECT"}
                                            onChange={(_, d) => patch({escalation: {
                                                afterSlaBreach: d.value as EscalationAction,
                                                escalateTo: step.escalation?.escalateTo ?? [],
                                            }})}
                                            size="small"
                                        >
                                            <option value="AUTO_REJECT">Reject automatically</option>
                                            <option value="AUTO_APPROVE">Approve automatically</option>
                                            <option value="ESCALATE">Escalate to someone else</option>
                                        </Select>
                                    </div>
                                )}
                            </>
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
            )}

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
