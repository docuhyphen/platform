import {ReactNode} from 'react';
import {Text} from '@fluentui/react-components';
import {SchemaDefinitionDto} from '../../../../models/models';
import {useSchemaTableRowStyles} from './SchemaTableRowStyles';

interface Props
{
    schema: SchemaDefinitionDto;
    canManage: boolean;
    statusBadges: ReactNode;
    actionsMenu: ReactNode;
}

const SchemaTableRow = ({schema, canManage, statusBadges, actionsMenu}: Props) =>
{
    const styles = useSchemaTableRowStyles();

    return (
        <tr id={"schema-card-tr-1"}
            className={styles.tr}>
            <td id={"schema-card-td-1"}
                className={styles.td}>
                <Text id={"schema-card-text-1"}
                      weight="semibold">{schema.displayName}</Text>
            </td>
            <td id={"schema-card-td-2"}
                className={styles.td}>
                <code id={"schema-card-code-1"}
                      className={styles.cardFieldKey}>
                    {schema.namespace}:{schema.schemaKey}
                </code>
            </td>
            <td id={"schema-card-td-3"}
                className={styles.td}>
                {statusBadges}
            </td>
            {canManage && (
                <td id={"schema-card-td-4"}
                    className={styles.td}>
                    {actionsMenu}
                </td>
            )}
        </tr>
    );
};

export default SchemaTableRow;
