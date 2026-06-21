import React, {useCallback, useEffect, useRef, useState} from "react";
import {
    Badge, Button, Combobox, Dialog, DialogActions, DialogBody, DialogContent, DialogSurface,
    DialogTitle, Divider, Input, Option, Select, Text,
} from "@fluentui/react-components";
import {
    AssigneeSpecDraft,
    EscalationAction,
    QuorumKind,
    StepOutcomeSpecDraft,
    WorkflowEntityRefDto,
    WorkflowStepSpecDraft,
    WorkflowStepType,
    WorkflowSubjectFieldDto,
    WorkflowTriggerEventDto,
} from "../../models/models.tsx";
import {lookupWorkflowEntities} from "../../../services/workflowService.ts";
import {useStepCardStyles} from "./StepCardStyles.tsx";
import {DeleteIcon, ToggleHeaderDownIcon, ToggleHeaderUpIcon} from "../../components/IconBundles.tsx";
import AssigneeBuilder from "./AssigneeBuilder.tsx";
import {formatTriggerName} from "./workflowUtils.ts";

const STEP_TYPE_LABELS: Record<WorkflowStepType, string> = {
    APPROVAL: "Approval", NOTIFICATION: "Notification", CONDITION: "Condition", ACTION: "Action",
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

    return (
        <Combobox
            size="small"
            style={{flex: "1 1 8rem", minWidth: 0}}
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
                            <span style={{color: "var(--colorNeutralForeground3)", marginLeft: "0.5rem", fontSize: "0.8em"}}>
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
                <Input size="small"
                       placeholder="$subject.fieldName == 'value'"
                       value={expression ?? ""}
                       onChange={(_, d) => onChange(d.value || undefined)}/>
                <Text size={100} style={{color: "var(--colorNeutralForeground3)"}}>
                    Select a trigger event to enable the expression builder.
                </Text>
            </div>
        );
    }

    return (
        <div className={`${styles.field} ${styles.fullWidth}`}>
            <Text size={200} weight="semibold">Condition</Text>
            <div style={{display: "flex", gap: "0.5rem", flexWrap: "wrap", alignItems: "center"}}>
                <Combobox
                    size="small"
                    style={{flex: "1 1 10rem", minWidth: 0}}
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

                <Select size="small" style={{flex: "1 1 10rem"}}
                        value={operator}
                        onChange={(_, d) =>
                        {
                            setOperator(d.value);
                            emit(field, d.value, value, quoted);
                        }}>
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
                    <Select size="small" style={{flex: "1 1 8rem"}}
                            value={value}
                            onChange={(_, d) =>
                            {
                                setValue(d.value);
                                setValueLabel(humanizeEnumValue(d.value));
                                emit(field, operator, d.value, quoted);
                            }}>
                        <option value="">Select…</option>
                        {enumValues.map(v => (
                            <option key={v} value={v}>{humanizeEnumValue(v)}</option>
                        ))}
                    </Select>
                ) : isBoolean ? (
                    <Select size="small" style={{flex: "1 1 8rem"}}
                            value={value}
                            onChange={(_, d) =>
                            {
                                setValue(d.value);
                                setValueLabel(d.value === "true" ? "Yes" : "No");
                                emit(field, operator, d.value, false);
                            }}>
                        <option value="">Select…</option>
                        <option value="true">Yes</option>
                        <option value="false">No</option>
                    </Select>
                ) : (
                    <Input size="small" style={{flex: "1 1 8rem"}}
                           type={isNumeric ? "number" : "text"}
                           placeholder={isNumeric ? "e.g. 5" : "Value…"}
                           value={value}
                           onChange={(_, d) =>
                           {
                               setValue(d.value);
                               setValueLabel(d.value);
                               emit(field, operator, d.value, quoted);
                           }}/>
                )}
            </div>
            {field && value && valueLabel && (
                <Text size={100} style={{color: "var(--colorNeutralForeground3)"}}>
                    {humanizeFieldName(field)} {allowedOperators.find(op => op.value === operator)?.label ?? operator} {valueLabel}
                </Text>
            )}
        </div>
    );
};

const BUILT_IN_ACTION_KEYS = ["exchange.auto-accept", "exchange.send-reminder", "exchange.revoke-access"];

const KNOWN_TEMPLATE_KEYS = [
    "exchange.reminder",
    "exchange.status-accepted",
    "exchange.status-rejected",
    "exchange.status-ended",
    "exchange.document-uploaded",
    "exchange.no-auth-otp",
];

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

const DELETE_COUNTDOWN = 5;

const StepCard = ({index, step, stepCount, onChange, onRemove, triggers, subjectFields}: Props) =>
{
    const styles = useStepCardStyles();
    const [expanded, setExpanded] = React.useState(true);
    const [showDeleteDialog, setShowDeleteDialog] = useState(false);
    const [deleteStarted, setDeleteStarted] = useState(false);
    const [countdown, setCountdown] = useState(DELETE_COUNTDOWN);
    const timerRef = React.useRef<ReturnType<typeof setInterval> | null>(null);

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
                <Button size="small" appearance="subtle" icon={<DeleteIcon/>}
                        onClick={(e) => { e.stopPropagation(); setShowDeleteDialog(true); }}
                        aria-label="Remove step"/>
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
                                <Button appearance="primary" shape="circular" onClick={onDeleteCancel}>
                                    Cancel
                                </Button>
                            ) : (
                                <>
                                    <Button appearance="primary" shape="circular" onClick={onDeleteConfirm}>
                                        Yes, Delete
                                    </Button>
                                    <Button appearance="secondary" shape="circular" onClick={onDialogDismiss}>
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
                            <Input size="small" placeholder="e.g. Manager Approval"
                                   value={step.name ?? ""}
                                   onChange={(_, d) => patch({name: d.value || undefined})}/>
                        </div>

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
                                    <Combobox
                                        size="small"
                                        freeform
                                        placeholder="Select or type a template key"
                                        value={step.messageTemplateKey ?? ""}
                                        selectedOptions={step.messageTemplateKey ? [step.messageTemplateKey] : []}
                                        onOptionSelect={(_, d) => patch({messageTemplateKey: d.optionValue || undefined})}
                                        onChange={e => patch({messageTemplateKey: e.target.value || undefined})}
                                    >
                                        {KNOWN_TEMPLATE_KEYS.map(k => (
                                            <Option key={k} value={k}>{formatTriggerName(k)}</Option>
                                        ))}
                                    </Combobox>
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

                    {(step.type === "APPROVAL" || step.type === "CONDITION") && <Divider/>}

                    {step.type === "APPROVAL" && (
                        <div className={styles.outcomeRow}>
                            <OutcomeField label="On Approve" value={step.onApprove} stepCount={stepCount}
                                          triggers={triggers} onChange={v => patch({onApprove: v})}/>
                            <OutcomeField label="On Reject" value={step.onReject} stepCount={stepCount}
                                          triggers={triggers} onChange={v => patch({onReject: v})}/>
                        </div>
                    )}

                    {step.type === "CONDITION" && (
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
