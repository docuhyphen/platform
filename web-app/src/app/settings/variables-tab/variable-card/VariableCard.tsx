import {Badge, Text} from '@fluentui/react-components';
import {VariableDefinitionDto} from '../../../models/models';
import {variableCardMenuIds, VariableIdScheme} from '../variableIds.ts';
import CopyTokenButton from '../copy-token-button/CopyTokenButton.tsx';
import VariableActionsMenu from '../variable-actions-menu/VariableActionsMenu.tsx';
import {useVariableCardStyles} from './VariableCardStyles.tsx';

interface VariableCardProps
{
    ids: VariableIdScheme;
    variable: VariableDefinitionDto;
    canManage: boolean;
    copiedToken: string | null;
    onCopy: (token: string) => void;
    onEdit: (v: VariableDefinitionDto) => void;
    onDelete: (v: VariableDefinitionDto) => void;
}

const VariableCard = ({ids, variable, canManage, copiedToken, onCopy, onEdit, onDelete}: VariableCardProps) =>
{
    const styles = useVariableCardStyles();

    return (
        <div
            id={`${ids.tab}-div-2-${variable.id}`}
            className={styles.variableRow}
        >
            <div id={`${ids.tab}-div-3`}
                 className={styles.variableRowInner}>
                <CopyTokenButton id={`${ids.button}-copy-${variable.id}`}
                                 token={variable.key}
                                 copiedToken={copiedToken}
                                 ariaLabel={`Copy variable ${variable.key}`}
                                 onCopy={onCopy}/>
                <code id={`${ids.tab}-code-1`}
                      className={styles.codeKey}>{variable.key}</code>
                <Text
                    id={`${ids.tab}-text-4`}
                    size={200}
                    className={styles.defaultValueText}
                >
                    {variable.defaultValue ? `Default: "${variable.defaultValue}"` : <em id={`${ids.tab}-em-1`}>no default</em>}
                </Text>
                {!variable.isActive && (
                    <Badge
                        id={`${ids.tab}-badge-1`}
                        appearance="tint"
                        color="severe"
                        size="small"
                    >
                        Inactive
                    </Badge>
                )}
            </div>
            <div id={`${ids.tab}-div-4`}
                 className={styles.actionGroup}>
                {canManage && (
                    <VariableActionsMenu variable={variable}
                                         ids={variableCardMenuIds(ids, variable.id)}
                                         onEdit={onEdit}
                                         onDelete={onDelete}/>
                )}
            </div>
        </div>
    );
};

export default VariableCard;
