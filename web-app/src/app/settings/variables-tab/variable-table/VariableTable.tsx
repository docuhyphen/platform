import {VariableDefinitionDto} from '../../../models/models';
import {VariableIdScheme} from '../variableIds.ts';
import VariableTableRow from './VariableTableRow.tsx';
import {useVariableTableStyles} from './VariableTableStyles.tsx';

interface VariableTableProps
{
    ids: VariableIdScheme;
    variables: VariableDefinitionDto[];
    canManage: boolean;
    copiedToken: string | null;
    onCopy: (token: string) => void;
    onEdit: (v: VariableDefinitionDto) => void;
    onDelete: (v: VariableDefinitionDto) => void;
}

const VariableTable = ({ids, variables, canManage, copiedToken, onCopy, onEdit, onDelete}: VariableTableProps) =>
{
    const styles = useVariableTableStyles();

    return (
        <table id={`${ids.tab}-table-1`}
               className={styles.table}>
            <thead id={`${ids.tab}-thead-1`}>
                <tr id={`${ids.tab}-tr-1`}>
                    <th id={`${ids.tab}-th-1`}
                        className={styles.th}>Token</th>
                    <th id={`${ids.tab}-th-2`}
                        className={styles.th}>Default Value</th>
                    <th id={`${ids.tab}-th-3`}
                        className={styles.th}>Active</th>
                    {canManage && <th id={`${ids.tab}-th-4`}
                                      className={styles.th}/>}
                </tr>
            </thead>
            <tbody id={`${ids.tab}-tbody-1`}>
                {variables.map(v => (
                    <VariableTableRow key={v.id}
                                      ids={ids}
                                      variable={v}
                                      canManage={canManage}
                                      copiedToken={copiedToken}
                                      onCopy={onCopy}
                                      onEdit={onEdit}
                                      onDelete={onDelete}/>
                ))}
            </tbody>
        </table>
    );
};

export default VariableTable;
