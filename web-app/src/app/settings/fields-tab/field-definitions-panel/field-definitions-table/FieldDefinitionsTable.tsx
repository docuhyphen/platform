import {Badge, Text} from '@fluentui/react-components';
import {FieldDefinitionDto} from '../../../../models/models';
import {VALUE_TYPE_LABELS} from '../../fieldLabels';
import FieldDefinitionActionsMenu from '../field-definition-actions-menu/FieldDefinitionActionsMenu';
import {useFieldDefinitionsTableStyles} from './FieldDefinitionsTableStyles';

interface Props
{
    definitions: FieldDefinitionDto[];
    canManage: (definition: FieldDefinitionDto) => boolean;
    onRetire: (definition: FieldDefinitionDto) => void;
}

const FieldDefinitionsTable = ({definitions, canManage, onRetire}: Props) =>
{
    const styles = useFieldDefinitionsTableStyles();
    const showActions = definitions.some(canManage);

    return (
        <table id={"field-definitions-panel-table-1"}
               className={styles.table}>
            <thead id={"field-definitions-panel-thead-1"}>
                <tr id={"field-definitions-panel-tr-1"}>
                    <th id={"field-definitions-panel-th-1"}
                        className={styles.th}>Name</th>
                    <th id={"field-definitions-panel-th-2"}
                        className={styles.th}>Key</th>
                    <th id={"field-definitions-panel-th-3"}
                        className={styles.th}>Type</th>
                    {showActions && <th id={"field-definitions-panel-th-4"}
                                        className={styles.th}/>}
                </tr>
            </thead>
            <tbody id={"field-definitions-panel-tbody-1"}>
                {definitions.map(definition => (
                    <tr id={`field-definitions-panel-tr-2-${definition.id}`}
                        key={definition.id}
                        className={styles.tr}>
                        <td id={"field-definitions-panel-td-1"}
                            className={styles.td}>
                            <Text id={"field-definitions-panel-text-3"}
                                  weight="semibold">
                                {definition.latestContract?.label ?? definition.fieldKey}
                            </Text>
                        </td>
                        <td id={"field-definitions-panel-td-2"}
                            className={styles.td}>
                            <code id={"field-definitions-panel-code-1"}
                                  className={styles.cardFieldKey}>
                                {definition.namespace}:{definition.fieldKey}
                            </code>
                        </td>
                        <td id={"field-definitions-panel-td-3"}
                            className={styles.td}>
                            {definition.latestContract && (
                                <Badge id={"field-definitions-panel-badge-1"}
                                       appearance="tint"
                                       color="informative"
                                       size="small">
                                    {VALUE_TYPE_LABELS[definition.latestContract.valueType]}
                                </Badge>
                            )}
                        </td>
                        {showActions && (
                            <td id={"field-definitions-panel-td-4"}
                                className={styles.td}>
                                {canManage(definition) ? (
                                    <FieldDefinitionActionsMenu definition={definition}
                                                                onRetire={onRetire}/>
                                ) : null}
                            </td>
                        )}
                    </tr>
                ))}
            </tbody>
        </table>
    );
};

export default FieldDefinitionsTable;
