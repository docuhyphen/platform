import React from 'react';
import {AvailableVariablesDto} from '../../../app/models/models';
import {useVariableTokenPreviewStyles} from "./VariableTokenPreviewStyles.tsx";
import VariableTokenChip from "../token-chip/VariableTokenChip.tsx";
import {VariableTokenMatch} from "../variableTokens.ts";

interface VariableTokenPreviewProps
{
    value: string;
    tokens: VariableTokenMatch[];
    availableVariables: AvailableVariablesDto;
    resolvedPreview?: Record<string, string>;
    disabled: boolean;
    onRemoveToken: (start: number, end: number) => void;
}

const VariableTokenPreview: React.FC<VariableTokenPreviewProps> = (
    {
        value,
        tokens,
        availableVariables,
        resolvedPreview,
        disabled,
        onRemoveToken,
    }) =>
{
    const styles = useVariableTokenPreviewStyles();

    const renderParts = () =>
    {
        const parts: React.ReactNode[] = [];
        let last = 0;
        tokens.forEach(({token, start, end}, i) =>
        {
            if (start > last)
                parts.push(<span id={`variable-token-input-span-1-${`text-${i}`}`}
                                 key={`text-${i}`}>{value.slice(last, start)}</span>);
            parts.push(
                <VariableTokenChip
                    key={`chip-${i}`}
                    token={token}
                    available={availableVariables}
                    resolved={resolvedPreview}
                    removeButtonId={`variable-token-remove-${i}`}
                    onRemove={() => onRemoveToken(start, end)}
                    disabled={disabled}
                />
            );
            last = end;
        });
        if (last < value.length)
            parts.push(<span id={"variable-token-input-span-2-text-end"}
                             key="text-end">{value.slice(last)}</span>);
        return parts;
    };

    return (
        <div id={"variable-token-input-div-5"}
             className={styles.preview}>
            {renderParts()}
        </div>
    );
};

export default VariableTokenPreview;
