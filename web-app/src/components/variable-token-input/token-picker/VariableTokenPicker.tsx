import React from 'react';
import {Input, Popover, PopoverSurface, PopoverTrigger, Text} from '@fluentui/react-components';
import {AvailableVariablesDto, SequenceDefinitionDto, SystemVariableDto, VariableDefinitionDto} from '../../../app/models/models';
import {useVariableTokenPickerStyles} from "./VariableTokenPickerStyles.tsx";
import VariableTokenPickerGroup from "../picker-group/VariableTokenPickerGroup.tsx";

interface VariableTokenPickerProps
{
    open: boolean;
    onOpenChange: (open: boolean) => void;
    search: string;
    onSearchChange: (search: string) => void;
    availableVariables: AvailableVariablesDto;
    onSelect: (token: string) => void;
}

const VariableTokenPicker: React.FC<VariableTokenPickerProps> = (
    {
        open,
        onOpenChange,
        search,
        onSearchChange,
        availableVariables,
        onSelect,
    }) =>
{
    const styles = useVariableTokenPickerStyles();

    const filterSearch = (token: string) =>
        !search || token.toLowerCase().includes(search.toLowerCase());

    const variableLabel = (v: VariableDefinitionDto) => `${v.key}${v.defaultValue ? ` = ${v.defaultValue}` : ''}`;

    return (
        <Popover open={open}
                 onOpenChange={(_, d) => onOpenChange(d.open)}>
            <PopoverTrigger disableButtonEnhancement>
                <span id={"variable-token-input-span-3"}/>
            </PopoverTrigger>
            <PopoverSurface id={"variable-token-input-popover-surface-1"}
                            className={styles.pickerSurface}>
                <Text id={"variable-token-input-text-3"}
                      weight="semibold"
                      size={300}
                      block
                      className={styles.pickerTitle}>Insert Variable</Text>
                <Input
                    id={"variable-token-input-input-1"}
                    size="small"
                    placeholder="Search variables…"
                    value={search}
                    onChange={(_, d) => onSearchChange(d.value)}
                    className={styles.pickerSearch}
                />
                <VariableTokenPickerGroup<SystemVariableDto>
                    label="System"
                    items={availableVariables.system.filter(s => filterSearch(s.token))}
                    getKey={s => s.token}
                    getLabel={s => s.description}
                    getBadge={s => s.token}
                    badgeColor="brand"
                    onSelect={onSelect}
                />
                <VariableTokenPickerGroup<SequenceDefinitionDto>
                    label="Sequences"
                    items={availableVariables.sequences.filter(s => filterSearch(`SEQ:${s.key}`))}
                    getKey={s => `SEQ:${s.key}`}
                    getLabel={s => `${s.name} (next: ${s.previewValue})`}
                    getBadge={s => `SEQ:${s.key}`}
                    badgeColor="success"
                    onSelect={onSelect}
                />
                <VariableTokenPickerGroup<VariableDefinitionDto>
                    label="Org Variables"
                    items={availableVariables.org.filter(v => filterSearch(v.key))}
                    getKey={v => v.key}
                    getLabel={variableLabel}
                    getBadge={v => v.key}
                    badgeColor="warning"
                    onSelect={onSelect}
                />
                <VariableTokenPickerGroup<VariableDefinitionDto>
                    label="Personal Variables"
                    items={availableVariables.personal.filter(v => filterSearch(v.key))}
                    getKey={v => v.key}
                    getLabel={variableLabel}
                    getBadge={v => v.key}
                    badgeColor="informative"
                    onSelect={onSelect}
                />
            </PopoverSurface>
        </Popover>
    );
};

export default VariableTokenPicker;
