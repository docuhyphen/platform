import {Dispatch, SetStateAction} from 'react';
import {Field, Input, Select} from '@fluentui/react-components';
import {CreateSequenceRequest, SequenceResetPeriod} from '../../../models/models';
import {RESET_PERIODS} from '../sequenceForm.ts';

interface SequenceFormFieldsProps
{
    form: CreateSequenceRequest;
    setForm: Dispatch<SetStateAction<CreateSequenceRequest>>;
    formError: string | null;
    isEditing: boolean;
}

const SequenceFormFields = ({form, setForm, formError, isEditing}: SequenceFormFieldsProps) => (
    <>
        <Field
            id={"organization-sequences-tab-field-1"}
            label="Name"
            required
            validationMessage={!form.name.trim() && formError ? formError : undefined}
        >
            <Input
                id={"input-seq-name"}
                value={form.name}
                onChange={(_, d) => setForm(f => ({...f, name: d.value}))}
                placeholder="e.g. Reference Number"
            />
        </Field>
        <Field
            id={"organization-sequences-tab-field-2"}
            label="Key (uppercase)"
            required
            hint="Used in {{SEQ:KEY}} token"
        >
            <Input
                id={"input-seq-key"}
                value={form.key}
                onChange={(_, d) => setForm(f => ({...f, key: d.value.toUpperCase()}))}
                placeholder="e.g. INV"
                disabled={isEditing}
            />
        </Field>
        <Field
            id={"organization-sequences-tab-field-3"}
            label="Pad Width"
            hint="0 = no padding, 3  to  007"
        >
            <Input
                id={"input-seq-pad-width"}
                type="number"
                value={String(form.padWidth ?? 0)}
                onChange={(_, d) => setForm(f => ({...f, padWidth: parseInt(d.value) || 0}))}
            />
        </Field>
        <Field id={"organization-sequences-tab-field-4"}
               label="Prefix">
            <Input
                id={"input-seq-prefix"}
                value={form.prefix ?? ''}
                onChange={(_, d) => setForm(f => ({...f, prefix: d.value}))}
                placeholder="e.g. INV-"
            />
        </Field>
        <Field id={"organization-sequences-tab-field-5"}
               label="Suffix">
            <Input
                id={"input-seq-suffix"}
                value={form.suffix ?? ''}
                onChange={(_, d) => setForm(f => ({...f, suffix: d.value}))}
                placeholder="e.g. /2026"
            />
        </Field>
        <Field id={"organization-sequences-tab-field-6"}
               label="Reset Period">
            <Select
                id={"select-seq-reset-period"}
                value={form.resetPeriod ?? 'NEVER'}
                onChange={(_, d) => setForm(f => ({...f, resetPeriod: d.value as SequenceResetPeriod}))}
            >
                {RESET_PERIODS.map(p => <option id={`organization-sequences-tab-option-1-${p}`}
                                                key={p}
                                                value={p}>{p}</option>)}
            </Select>
        </Field>
    </>
);

export default SequenceFormFields;
