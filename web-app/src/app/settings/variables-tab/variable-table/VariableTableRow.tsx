import {Text} from '@fluentui/react-components';
import {VariableDefinitionDto} from '../../../models/models';
import {VariableIdScheme, variableTableMenuIds} from '../variableIds.ts';
import CopyTokenButton from '../copy-token-button/CopyTokenButton.tsx';
import VariableActionsMenu from '../variable-actions-menu/VariableActionsMenu.tsx';
import {useVariableTableStyles} from './VariableTableStyles.tsx';

interface VariableTableRowProps
{
    ids: VariableIdScheme;
    variable: VariableDefinitionDto;
    canManage: boolean;
    copiedToken: string | null;
    onCopy: (token: string) => void;
    onEdit: (v: VariableDefinitionDto) => void;
    onDelete: (v: VariableDefinitionDto) => void;
}

const VariableTableRow = ({ids, variable, canManage, copiedToken, onCopy, onEdit, onDelete}: VariableTableRowProps) =>
{
    const styles = useVariableTableStyles();

    return (
        <tr id={`${ids.tab}-tr-2-${variable.id}`}
            className={styles.tr}>
            <td id={`${ids.tab}-td-1`}
                className={styles.td}>
                <div id={`${ids.tab}-div-5`}
                     className={styles.tokenCell}>
                    <CopyTokenButton id={`${ids.button}-copy-table-${variable.id}`}
                                     token={variable.key}
                                     copiedToken={copiedToken}
                                     ariaLabel={`Copy variable ${variable.key}`}
                                     onCopy={onCopy}/>
                    <code id={`${ids.tab}-code-2`}
                          className={styles.codeKey}>{variable.key}</code>
                </div>
            </td>
            <td id={`${ids.tab}-td-2`}
                className={styles.td}>
                <Text id={`${ids.tab}-text-5`}
                      size={200}>{variable.defaultValue || <em id={`${ids.tab}-em-2`}>no default</em>}</Text>
            </td>
            <td id={`${ids.tab}-td-3`}
                className={styles.td}>
                <Text id={`${ids.tab}-text-6`}
                      size={200}>{variable.isActive ? 'Active' : 'Inactive'}</Text>
            </td>
            {canManage && (
                <td id={`${ids.tab}-td-4`}
                    className={styles.td}>
                    <VariableActionsMenu variable={variable}
                                         ids={variableTableMenuIds(ids, variable.id)}
                                         onEdit={onEdit}
                                         onDelete={onDelete}/>
                </td>
            )}
        </tr>
    );
};

export default VariableTableRow;
