import React, {useRef, useState} from 'react';
import {
    Badge,
    Button,
    Input,
    Label,
    Popover,
    PopoverSurface,
    PopoverTrigger,
    Text,
    Textarea,
    Tooltip,
} from '@fluentui/react-components';
import {DismissRegular} from '@fluentui/react-icons';
import {AvailableVariablesDto, SequenceDefinitionDto, SystemVariableDto, VariableDefinitionDto} from '../../app/models/models';

export interface VariableTokenInputProps
{
    value: string;
    onChange: (value: string) => void;
    availableVariables: AvailableVariablesDto;
    resolvedPreview?: Record<string, string>;
    multiline?: boolean;
    placeholder?: string;
    label?: string;
    disabled?: boolean;
}

const TOKEN_RE = /\{\{([^}]+)}}/g;

function getTokens(value: string): Array<{token: string; start: number; end: number}>
{
    const tokens: Array<{token: string; start: number; end: number}> = [];
    let m: RegExpExecArray | null;
    TOKEN_RE.lastIndex = 0;
    while ((m = TOKEN_RE.exec(value)) !== null)
        tokens.push({token: m[1].trim(), start: m.index, end: m.index + m[0].length});
    return tokens;
}

function getPreviewLabel(token: string, available: AvailableVariablesDto, resolved?: Record<string, string>): string
{
    if (resolved?.[token]) return resolved[token];
    const sys = available.system.find(s => s.token === token);
    if (sys) return sys.example;
    const seq = available.sequences.find(s => `SEQ:${s.key}` === token);
    if (seq) return seq.previewValue;
    const orgVar = available.org.find(v => v.key === token);
    if (orgVar) return orgVar.defaultValue ?? '';
    const personalVar = available.personal.find(v => v.key === token);
    if (personalVar) return personalVar.defaultValue ?? '';
    return `{{${token}}}`;
}

interface TokenChipProps
{
    token: string;
    available: AvailableVariablesDto;
    resolved?: Record<string, string>;
    onRemove: () => void;
    disabled?: boolean;
}

const TokenChip: React.FC<TokenChipProps> = ({token, available, resolved, onRemove, disabled}) =>
{
    const isSystem = available.system.some(s => s.token === token);
    const isSeq = token.startsWith('SEQ:');
    const isOrg = available.org.some(v => v.key === token);

    let color: 'brand' | 'success' | 'informative' | 'warning' = 'informative';
    if (isSystem) color = 'brand';
    else if (isSeq) color = 'success';
    else if (isOrg) color = 'warning';

    const preview = getPreviewLabel(token, available, resolved);

    return (
        <Tooltip content={`{{${token}}} → ${preview}`} relationship="description">
            <Badge
                appearance="tint"
                color={color}
                style={{cursor: 'default', display: 'inline-flex', alignItems: 'center', gap: '2px', margin: '0 2px'}}
            >
                {token}
                {!disabled && (
                    <Button
                        size="small"
                        appearance="transparent"
                        icon={<DismissRegular style={{fontSize: '10px'}}/>}
                        onClick={onRemove}
                        style={{minWidth: 0, padding: '0 2px', height: '16px'}}
                        aria-label={`Remove ${token}`}
                    />
                )}
            </Badge>
        </Tooltip>
    );
};

interface PickerGroupProps<T>
{
    label: string;
    items: T[];
    getKey: (item: T) => string;
    getLabel: (item: T) => string;
    getBadge: (item: T) => string;
    badgeColor: 'brand' | 'success' | 'warning' | 'informative';
    onSelect: (token: string) => void;
}

function PickerGroup<T>({label, items, getKey, getLabel, getBadge, badgeColor, onSelect}: PickerGroupProps<T>)
{
    if (items.length === 0) return null;
    return (
        <div style={{marginBottom: '8px'}}>
            <Text size={100} weight="semibold" style={{color: 'var(--colorNeutralForeground3)', display: 'block', marginBottom: '4px', textTransform: 'uppercase', letterSpacing: '0.5px'}}>
                {label}
            </Text>
            <div style={{display: 'flex', flexDirection: 'column', gap: '2px'}}>
                {items.map(item => (
                    <Button
                        key={getKey(item)}
                        appearance="subtle"
                        size="small"
                        style={{justifyContent: 'flex-start', gap: '8px', padding: '4px 6px'}}
                        onClick={() => onSelect(getKey(item))}
                    >
                        <Badge appearance="tint" color={badgeColor} size="small">{getBadge(item)}</Badge>
                        <Text size={200}>{getLabel(item)}</Text>
                    </Button>
                ))}
            </div>
        </div>
    );
}

const VariableTokenInput: React.FC<VariableTokenInputProps> = ({
    value,
    onChange,
    availableVariables,
    resolvedPreview,
    multiline = false,
    placeholder,
    label,
    disabled = false,
}) =>
{
    const [pickerOpen, setPickerOpen] = useState(false);
    const [pickerSearch, setPickerSearch] = useState('');
    const inputRef = useRef<HTMLInputElement | HTMLTextAreaElement | null>(null);
    const cursorRef = useRef<number>(value.length);

    const insertToken = (token: string) =>
    {
        const pos = cursorRef.current;
        const newValue = value.slice(0, pos) + `{{${token}}}` + value.slice(pos);
        onChange(newValue);
        setPickerOpen(false);
        setPickerSearch('');
    };

    const removeToken = (start: number, end: number) =>
    {
        onChange(value.slice(0, start) + value.slice(end));
    };

    const handleInputChange = (raw: string) =>
    {
        if (raw.endsWith('{{'))
        {
            setPickerOpen(true);
        }
        onChange(raw);
    };

    const filterSearch = (token: string) =>
        !pickerSearch || token.toLowerCase().includes(pickerSearch.toLowerCase());

    const tokens = getTokens(value);

    // Render a visual preview of the value with chips interspersed
    const renderPreview = () =>
    {
        if (tokens.length === 0) return null;
        const parts: React.ReactNode[] = [];
        let last = 0;
        tokens.forEach(({token, start, end}, i) =>
        {
            if (start > last)
                parts.push(<span key={`text-${i}`}>{value.slice(last, start)}</span>);
            parts.push(
                <TokenChip
                    key={`chip-${i}`}
                    token={token}
                    available={availableVariables}
                    resolved={resolvedPreview}
                    onRemove={() => removeToken(start, end)}
                    disabled={disabled}
                />
            );
            last = end;
        });
        if (last < value.length)
            parts.push(<span key="text-end">{value.slice(last)}</span>);
        return parts;
    };

    const picker = (
        <Popover open={pickerOpen} onOpenChange={(_, d) => setPickerOpen(d.open)}>
            <PopoverTrigger disableButtonEnhancement>
                <span/>
            </PopoverTrigger>
            <PopoverSurface style={{padding: '12px', minWidth: '260px', maxWidth: '320px', maxHeight: '400px', overflowY: 'auto'}}>
                <Text weight="semibold" size={300} block style={{marginBottom: '8px'}}>Insert Variable</Text>
                <Input
                    size="small"
                    placeholder="Search variables…"
                    value={pickerSearch}
                    onChange={(_, d) => setPickerSearch(d.value)}
                    style={{marginBottom: '8px', width: '100%'}}
                />
                <PickerGroup<SystemVariableDto>
                    label="System"
                    items={availableVariables.system.filter(s => filterSearch(s.token))}
                    getKey={s => s.token}
                    getLabel={s => s.description}
                    getBadge={s => s.token}
                    badgeColor="brand"
                    onSelect={insertToken}
                />
                <PickerGroup<SequenceDefinitionDto>
                    label="Sequences"
                    items={availableVariables.sequences.filter(s => filterSearch(`SEQ:${s.key}`))}
                    getKey={s => `SEQ:${s.key}`}
                    getLabel={s => `${s.name} (next: ${s.previewValue})`}
                    getBadge={s => `SEQ:${s.key}`}
                    badgeColor="success"
                    onSelect={insertToken}
                />
                <PickerGroup<VariableDefinitionDto>
                    label="Org Variables"
                    items={availableVariables.org.filter(v => filterSearch(v.key))}
                    getKey={v => v.key}
                    getLabel={v => `${v.key}${v.defaultValue ? ` = ${v.defaultValue}` : ''}`}
                    getBadge={v => v.key}
                    badgeColor="warning"
                    onSelect={insertToken}
                />
                <PickerGroup<VariableDefinitionDto>
                    label="Personal Variables"
                    items={availableVariables.personal.filter(v => filterSearch(v.key))}
                    getKey={v => v.key}
                    getLabel={v => `${v.key}${v.defaultValue ? ` = ${v.defaultValue}` : ''}`}
                    getBadge={v => v.key}
                    badgeColor="informative"
                    onSelect={insertToken}
                />
            </PopoverSurface>
        </Popover>
    );

    return (
        <div style={{display: 'flex', flexDirection: 'column', gap: '4px'}}>
            {label && <Label>{label}</Label>}
            <div style={{position: 'relative'}}>
                {multiline ? (
                    <Textarea
                        ref={inputRef as React.Ref<HTMLTextAreaElement>}
                        value={value}
                        onChange={(_, d) => handleInputChange(d.value)}
                        onSelect={e => { cursorRef.current = (e.target as HTMLTextAreaElement).selectionStart ?? value.length; }}
                        placeholder={placeholder}
                        disabled={disabled}
                        style={{width: '100%'}}
                    />
                ) : (
                    <Input
                        ref={inputRef as React.Ref<HTMLInputElement>}
                        value={value}
                        onChange={(_, d) => handleInputChange(d.value)}
                        onSelect={e => { cursorRef.current = (e.target as HTMLInputElement).selectionStart ?? value.length; }}
                        placeholder={placeholder}
                        disabled={disabled}
                        style={{width: '100%'}}
                        contentAfter={
                            <Button
                                size="small"
                                appearance="transparent"
                                onClick={() => setPickerOpen(true)}
                                disabled={disabled}
                                title="Insert variable"
                                style={{fontSize: '11px', padding: '0 4px', minWidth: 0}}
                            >
                                {'{ }'}
                            </Button>
                        }
                    />
                )}
                {picker}
            </div>
            {tokens.length > 0 && (
                <div style={{
                    padding: '6px 8px',
                    background: 'var(--colorNeutralBackground2)',
                    borderRadius: '4px',
                    fontSize: '12px',
                    lineHeight: '1.8',
                    wordBreak: 'break-word',
                }}>
                    {renderPreview()}
                </div>
            )}
        </div>
    );
};

export default VariableTokenInput;
