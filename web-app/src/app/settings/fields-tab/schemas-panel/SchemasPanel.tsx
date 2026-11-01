import {forwardRef, useImperativeHandle} from 'react';
import {Spinner, Text} from '@fluentui/react-components';
import {SchemaDefinitionDto, ViewMode} from '../../../models/models';
import SchemaEditorDialog from '../schema-editor-dialog/SchemaEditorDialog';
import SchemaCard from '../schema-card/SchemaCard';
import {useSchemaPanelActions} from './useSchemaPanelActions';
import {useSchemasPanelStyles} from './SchemasPanelStyles';

export interface SchemasPanelHandle
{
    openCreate: () => void;
}

interface Props
{
    schemas: SchemaDefinitionDto[];
    loading: boolean;
    error: string | null;
    viewMode: ViewMode;
    canManage: (schema: SchemaDefinitionDto) => boolean;
    onRefresh: () => void;
}

const SchemasPanel = forwardRef<SchemasPanelHandle, Props>(
    ({schemas, loading, error, viewMode, canManage, onRefresh}, ref) =>
    {
        const styles = useSchemasPanelStyles();
        const actions = useSchemaPanelActions(onRefresh);

        useImperativeHandle(ref, () => ({openCreate: actions.openCreate}));

        if (loading) return <Spinner id={"schemas-panel-spinner-1"}
                                     size="small"
                                     label="Loading..."/>;
        if (error) return <Text id={"schemas-panel-text-1"}
                                className={styles.errorText}>{error}</Text>;
        if (schemas.length === 0) return <Text id={"schemas-panel-text-2"}
                                               className={styles.emptyText}>No schemas yet.</Text>;

        const renderCard = (schema: SchemaDefinitionDto, cardViewMode: ViewMode) => (
            <SchemaCard key={schema.id}
                        schema={schema}
                        canManage={canManage(schema)}
                        viewMode={cardViewMode}
                        onEdit={actions.openEdit}
                        onPublish={actions.publish}
                        onNewVersion={actions.newVersion}
                        onRetire={actions.retire}/>
        );

        return (
            <div id="schemas-panel">
                {viewMode === 'table' ? (
                    <table id={"schemas-panel-table-1"}
                           className={styles.table}>
                        <thead id={"schemas-panel-thead-1"}>
                            <tr id={"schemas-panel-tr-1"}>
                                <th id={"schemas-panel-th-1"}
                                    className={styles.th}>Name</th>
                                <th id={"schemas-panel-th-2"}
                                    className={styles.th}>Key</th>
                                <th id={"schemas-panel-th-3"}
                                    className={styles.th}>Status</th>
                                {schemas.some(canManage) && <th id={"schemas-panel-th-4"}
                                                                className={styles.th}/>}
                            </tr>
                        </thead>
                        <tbody id={"schemas-panel-tbody-1"}>
                            {schemas.map(schema => renderCard(schema, 'table'))}
                        </tbody>
                    </table>
                ) : (
                    <div id={"schemas-panel-div-1"}
                         className={styles.cardGrid}>
                        {schemas.map(schema => renderCard(schema, 'cards'))}
                    </div>
                )}

                <SchemaEditorDialog open={actions.editorOpen}
                                    definitions={actions.definitions}
                                    schema={actions.editing}
                                    onClose={actions.closeEditor}
                                    onSaved={actions.editorSaved}/>
            </div>
        );
    },
);

SchemasPanel.displayName = 'SchemasPanel';

export default SchemasPanel;
