import {Badge, Button, Text} from '@fluentui/react-components';
import {useVariableTokenPickerGroupStyles} from "./VariableTokenPickerGroupStyles.tsx";
import {VariableTokenBadgeColor} from "../variableTokens.ts";

interface VariableTokenPickerGroupProps<T>
{
    label: string;
    items: T[];
    getKey: (item: T) => string;
    getLabel: (item: T) => string;
    getBadge: (item: T) => string;
    badgeColor: VariableTokenBadgeColor;
    onSelect: (token: string) => void;
}

function VariableTokenPickerGroup<T>({label, items, getKey, getLabel, getBadge, badgeColor, onSelect}: VariableTokenPickerGroupProps<T>)
{
    const styles = useVariableTokenPickerGroupStyles();
    if (items.length === 0) return null;
    return (
        <div id={"variable-token-input-div-1"}
             className={styles.pickerGroup}>
            <Text id={"variable-token-input-text-1"}
                  size={100}
                  weight="semibold"
                  className={styles.pickerGroupLabel}>
                {label}
            </Text>
            <div id={"variable-token-input-div-2"}
                 className={styles.pickerGroupList}>
                {items.map(item => (
                    <Button
                        id={`variable-token-picker-${label.toLowerCase().replace(/\s+/g, "-")}-${getKey(item)}`}
                        key={getKey(item)}
                        appearance="subtle"
                        size="small"
                        shape="circular"
                        className={styles.pickerGroupButton}
                        onClick={() => onSelect(getKey(item))}
                    >
                        <Badge id={"variable-token-input-badge-2"}
                               appearance="tint"
                               color={badgeColor}
                               size="small">{getBadge(item)}</Badge>
                        <Text id={"variable-token-input-text-2"}
                              size={200}>{getLabel(item)}</Text>
                    </Button>
                ))}
            </div>
        </div>
    );
}

export default VariableTokenPickerGroup;
