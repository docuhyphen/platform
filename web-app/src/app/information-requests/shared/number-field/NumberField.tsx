import {Field, Input} from "@fluentui/react-components";

interface NumberFieldProps
{
    id: string;
    label: string;
    value?: number;
    min?: number;
    max?: number;
    step?: number;
    disabled?: boolean;
    hint?: string;
    className?: string;
    onChange: (value: number | undefined) => void;
}

const parsed = (raw: string): number | undefined =>
{
    if (raw.trim() === "") return undefined;
    const value = Number(raw);
    return Number.isFinite(value) ? value : undefined;
};

const NumberField = ({id, label, value, min, max, step, disabled, hint, className, onChange}: NumberFieldProps) => (
    <Field id={`${id}-field`}
           label={label}
           hint={hint}
           className={className}>
        <Input id={id}
               type={"number"}
               inputMode={"decimal"}
               value={value === undefined ? "" : String(value)}
               min={min}
               max={max}
               step={step}
               disabled={disabled}
               onChange={(_, data) => onChange(parsed(data.value))}/>
    </Field>
);

export default NumberField;
