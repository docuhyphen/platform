import {Badge, Text} from '@fluentui/react-components';
import {SequenceDefinitionDto} from '../../../models/models';
import SequenceActionsMenu from '../sequence-actions-menu/SequenceActionsMenu.tsx';
import {sequenceTableMenuIds} from '../sequenceMenuIds.ts';
import {useSequenceTableStyles} from './SequenceTableStyles.tsx';

interface SequenceTableProps
{
    sequences: SequenceDefinitionDto[];
    canManage: boolean;
    onEdit: (seq: SequenceDefinitionDto) => void;
    onReset: (seq: SequenceDefinitionDto) => void;
    onDelete: (seq: SequenceDefinitionDto) => void;
}

const SequenceTable = ({sequences, canManage, onEdit, onReset, onDelete}: SequenceTableProps) =>
{
    const styles = useSequenceTableStyles();

    return (
        <table id={"organization-sequences-tab-table-1"}
               className={styles.table}>
            <thead id={"organization-sequences-tab-thead-1"}>
                <tr id={"organization-sequences-tab-tr-1"}>
                    <th id={"organization-sequences-tab-th-1"}
                        className={styles.th}>Name</th>
                    <th id={"organization-sequences-tab-th-2"}
                        className={styles.th}>Token</th>
                    <th id={"organization-sequences-tab-th-3"}
                        className={styles.th}>Counter</th>
                    <th id={"organization-sequences-tab-th-4"}
                        className={styles.th}>Reset Period</th>
                    <th id={"organization-sequences-tab-th-5"}
                        className={styles.th}>Active</th>
                    {canManage && <th id={"organization-sequences-tab-th-6"}
                                      className={styles.th}/>}
                </tr>
            </thead>
            <tbody id={"organization-sequences-tab-tbody-1"}>
                {sequences.map(seq => (
                    <tr id={`organization-sequences-tab-tr-2-${seq.id}`}
                        key={seq.id}
                        className={styles.tr}>
                        <td id={"organization-sequences-tab-td-1"}
                            className={styles.td}>
                            <Text id={"organization-sequences-tab-text-7"}
                                  weight="semibold">{seq.name}</Text>
                        </td>
                        <td id={"organization-sequences-tab-td-2"}
                            className={styles.td}>
                            <code id={"organization-sequences-tab-code-3"}
                                  className={styles.codeToken}>{`{{SEQ:${seq.key}}}`}</code>
                        </td>
                        <td id={"organization-sequences-tab-td-3"}
                            className={styles.td}>
                            <Text id={"organization-sequences-tab-text-8"}
                                  size={200}>{seq.currentValue} (next: {seq.previewValue})</Text>
                        </td>
                        <td id={"organization-sequences-tab-td-4"}
                            className={styles.td}>
                            <Text id={"organization-sequences-tab-text-9"}
                                  size={200}>{seq.resetPeriod}</Text>
                        </td>
                        <td id={"organization-sequences-tab-td-5"}
                            className={styles.td}>
                            <Badge id={"organization-sequences-tab-badge-4"}
                                   appearance="tint"
                                   color={seq.isActive ? 'success' : 'severe'}
                                   size="small">
                                {seq.isActive ? 'Active' : 'Inactive'}
                            </Badge>
                        </td>
                        {canManage && (
                            <td id={"organization-sequences-tab-td-6"}
                                className={styles.td}>
                                <SequenceActionsMenu sequence={seq}
                                                     ids={sequenceTableMenuIds(seq.id)}
                                                     onEdit={onEdit}
                                                     onReset={onReset}
                                                     onDelete={onDelete}/>
                            </td>
                        )}
                    </tr>
                ))}
            </tbody>
        </table>
    );
};

export default SequenceTable;
