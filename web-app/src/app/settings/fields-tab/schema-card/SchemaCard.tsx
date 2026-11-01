import {Text} from '@fluentui/react-components';
import {SchemaDefinitionDto, ViewMode} from '../../../models/models';
import SchemaActionsMenu from './schema-actions-menu/SchemaActionsMenu';
import SchemaStatusBadges from './schema-status-badges/SchemaStatusBadges';
import SchemaTableRow from './schema-table-row/SchemaTableRow';
import {useSchemaCardStyles} from './SchemaCardStyles';

interface Props
{
    schema: SchemaDefinitionDto;
    canManage: boolean;
    viewMode: ViewMode;
    onEdit: (schema: SchemaDefinitionDto) => void;
    onPublish: (schema: SchemaDefinitionDto) => void;
    onNewVersion: (schema: SchemaDefinitionDto) => void;
    onRetire: (schema: SchemaDefinitionDto) => void;
}

const SchemaCard = ({schema, canManage, viewMode, onEdit, onPublish, onNewVersion, onRetire}: Props) =>
{
    const styles = useSchemaCardStyles();

    const actionsMenu = canManage ? (
        <SchemaActionsMenu schema={schema}
                           onEdit={onEdit}
                           onPublish={onPublish}
                           onNewVersion={onNewVersion}
                           onRetire={onRetire}/>
    ) : null;

    const statusBadges = <SchemaStatusBadges schema={schema}/>;

    if (viewMode === 'table')
    {
        return (
            <SchemaTableRow schema={schema}
                            canManage={canManage}
                            statusBadges={statusBadges}
                            actionsMenu={actionsMenu}/>
        );
    }

    return (
        <div id={"schema-card-div-2"}
             className={styles.card}>
            <div id={"schema-card-div-3"}
                 className={styles.cardMain}>
                <Text id={"schema-card-text-2"}
                      className={styles.cardFieldName}
                      title={schema.displayName}>
                    {schema.displayName}
                </Text>
                {schema.description && (
                    <Text id={"schema-card-text-3"}
                          className={styles.subText}
                          title={schema.description}>
                        {schema.description}
                    </Text>
                )}
                <code id={"schema-card-code-2"}
                      className={styles.codeKey}>
                    {schema.namespace}:{schema.schemaKey}
                </code>
            </div>

            <div id={"schema-card-div-4"}
                 className={styles.schemaCardCol2}>
                {statusBadges}
                {actionsMenu}
            </div>
        </div>
    );
};

export default SchemaCard;
