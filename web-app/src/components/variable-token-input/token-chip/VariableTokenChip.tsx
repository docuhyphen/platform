import React from 'react';
import {Badge, Button, Tooltip} from '@fluentui/react-components';
import {DismissRegular} from '@fluentui/react-icons';
import {AvailableVariablesDto} from '../../../app/models/models';
import {useVariableTokenChipStyles} from "./VariableTokenChipStyles.tsx";
import {getPreviewLabel, getTokenBadgeColor} from "../variableTokens.ts";

interface VariableTokenChipProps
{
    token: string;
    available: AvailableVariablesDto;
    resolved?: Record<string, string>;
    removeButtonId: string;
    onRemove: () => void;
    disabled?: boolean;
}

const VariableTokenChip: React.FC<VariableTokenChipProps> = ({token, available, resolved, removeButtonId, onRemove, disabled}) =>
{
    const styles = useVariableTokenChipStyles();
    const color = getTokenBadgeColor(token, available);
    const preview = getPreviewLabel(token, available, resolved);

    return (
        <Tooltip content={`{{${token}}} resolves to ${preview}`}
                 relationship="description">
            <Badge
                id={"variable-token-input-badge-1"}
                appearance="tint"
                color={color}
                className={styles.tokenChip}
            >
                {token}
                {!disabled && (
                    <Button
                        id={removeButtonId}
                        size="small"
                        appearance="transparent"
                        shape="circular"
                        icon={<DismissRegular className={styles.removeIcon}/>}
                        onClick={onRemove}
                        className={styles.removeButton}
                        aria-label={`Remove ${token}`}
                    />
                )}
            </Badge>
        </Tooltip>
    );
};

export default VariableTokenChip;
