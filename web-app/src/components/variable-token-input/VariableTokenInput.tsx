import React from 'react';
import {Button, Input, Label, Textarea} from '@fluentui/react-components';
import {AvailableVariablesDto} from '../../app/models/models';
import {useVariableTokenInputStyles} from "./VariableTokenInputStyles.tsx";
import useVariableTokenInput from "./useVariableTokenInput.ts";
import VariableTokenPicker from "./token-picker/VariableTokenPicker.tsx";
import VariableTokenPreview from "./token-preview/VariableTokenPreview.tsx";

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
    const styles = useVariableTokenInputStyles();
    const {
        pickerOpen,
        setPickerOpen,
        pickerSearch,
        setPickerSearch,
        inputRef,
        insertToken,
        removeToken,
        handleInputChange,
        rememberCursor,
        tokens,
    } = useVariableTokenInput(value, onChange);

    return (
        <div id={"variable-token-input-div-3"}
             className={styles.root}>
            {label && <Label id={"variable-token-input-label-1"}>{label}</Label>}
            <div id={"variable-token-input-div-4"}
                 className={styles.inputWrapper}>
                {multiline ? (
                    <Textarea
                        id={"variable-token-input-textarea-1"}
                        ref={inputRef as React.Ref<HTMLTextAreaElement>}
                        value={value}
                        onChange={(_, d) => handleInputChange(d.value)}
                        onSelect={e => rememberCursor((e.target as HTMLTextAreaElement).selectionStart)}
                        placeholder={placeholder}
                        disabled={disabled}
                        className={styles.fullWidth}
                    />
                ) : (
                    <Input
                        id={"variable-token-input-input-2"}
                        ref={inputRef as React.Ref<HTMLInputElement>}
                        value={value}
                        onChange={(_, d) => handleInputChange(d.value)}
                        onSelect={e => rememberCursor((e.target as HTMLInputElement).selectionStart)}
                        placeholder={placeholder}
                        disabled={disabled}
                        className={styles.fullWidth}
                        contentAfter={
                            <Button
                                id="variable-token-insert-btn"
                                size="small"
                                appearance="transparent"
                                shape="circular"
                                onClick={() => setPickerOpen(true)}
                                disabled={disabled}
                                title="Insert variable"
                                className={styles.insertButton}
                            >
                                {'{ }'}
                            </Button>
                        }
                    />
                )}
                <VariableTokenPicker open={pickerOpen}
                                     onOpenChange={setPickerOpen}
                                     search={pickerSearch}
                                     onSearchChange={setPickerSearch}
                                     availableVariables={availableVariables}
                                     onSelect={insertToken}/>
            </div>
            {tokens.length > 0 && (
                <VariableTokenPreview value={value}
                                      tokens={tokens}
                                      availableVariables={availableVariables}
                                      resolvedPreview={resolvedPreview}
                                      disabled={disabled}
                                      onRemoveToken={removeToken}/>
            )}
        </div>
    );
};

export default VariableTokenInput;
