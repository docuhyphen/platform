import {forwardRef, useEffect, useImperativeHandle, useState} from 'react';
import {Spinner, Text} from '@fluentui/react-components';
import {FieldDefinitionDto, SchemaDefinitionDto, ViewMode} from '../../models/models';
import {
    createSchemaDraftVersion,
    getSchema,
    listFieldDefinitions,
    publishSchemaDraft,
    retireSchema,
} from '../../../services/fieldsService';
import {useFieldsTabStyles} from './FieldsTabStyles';
import SchemaEditorDialog from './SchemaEditorDialog';
import SchemaCard from './SchemaCard';

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
    canManage: boolean;
    onRefresh: () => void;
}

const SchemasPanel = forwardRef<SchemasPanelHandle, Props>(
    ({schemas, loading, error, viewMode, canManage, onRefresh}, ref) =>
    {
        const styles = useFieldsTabStyles();
        const [definitions, setDefinitions] = useState<FieldDefinitionDto[]>([]);
        const [editorOpen, setEditorOpen] = useState(false);
        const [editing, setEditing] = useState<SchemaDefinitionDto | undefined>(undefined);

        useEffect(() =>
        {
            listFieldDefinitions().then(setDefinitions).catch(() => null);
        }, []);

        const openCreate = () => { setEditing(undefined); setEditorOpen(true); };

        useImperativeHandle(ref, () => ({openCreate}));

        const openEdit = async (schema: SchemaDefinitionDto) =>
        {
            const fresh = schema.draftVersion ? schema : await getSchema(schema.id).catch(() => schema);
            setEditing(fresh);
            setEditorOpen(true);
        };

        const handlePublish = async (schema: SchemaDefinitionDto) =>
        {
            await publishSchemaDraft(schema.id).catch(() => null);
            onRefresh();
        };

        const handleNewVersion = async (schema: SchemaDefinitionDto) =>
        {
            await createSchemaDraftVersion(schema.id).catch(() => null);
            const fresh = await getSchema(schema.id).catch(() => undefined);
            if (fresh) { setEditing(fresh); setEditorOpen(true); }
            onRefresh();
        };

        const handleRetire = async (schema: SchemaDefinitionDto) =>
        {
            await retireSchema(schema.id).catch(() => null);
            onRefresh();
        };

        if (loading) return <Spinner size="small"
                                     label="Loading..."/>;
        if (error) return <Text className={styles.errorText}>{error}</Text>;
        if (schemas.length === 0) return <Text className={styles.emptyText}>No schemas yet.</Text>;

        return (
            <div id="schemas-panel">
                {viewMode === 'table' ? (
                    <table className={styles.table}>
                        <thead>
                            <tr>
                                <th className={styles.th}>Name</th>
                                <th className={styles.th}>Key</th>
                                <th className={styles.th}>Status</th>
                                {canManage && <th className={styles.th}/>}
                            </tr>
                        </thead>
                        <tbody>
                            {schemas.map(schema => (
                                <SchemaCard key={schema.id}
                                            schema={schema}
                                            canManage={canManage}
                                            viewMode="table"
                                            onEdit={openEdit}
                                            onPublish={handlePublish}
                                            onNewVersion={handleNewVersion}
                                            onRetire={handleRetire}/>
                            ))}
                        </tbody>
                    </table>
                ) : (
                    <div className={styles.cardGrid}>
                        {schemas.map(schema => (
                            <SchemaCard key={schema.id}
                                        schema={schema}
                                        canManage={canManage}
                                        viewMode="cards"
                                        onEdit={openEdit}
                                        onPublish={handlePublish}
                                        onNewVersion={handleNewVersion}
                                        onRetire={handleRetire}/>
                        ))}
                    </div>
                )}

                <SchemaEditorDialog open={editorOpen}
                                    definitions={definitions}
                                    schema={editing}
                                    onClose={() => setEditorOpen(false)}
                                    onSaved={() => { setEditorOpen(false); onRefresh(); }}/>
            </div>
        );
    },
);

SchemasPanel.displayName = 'SchemasPanel';

export default SchemasPanel;
