import {useEffect, useState} from 'react';
import {Button, Spinner, Text} from '@fluentui/react-components';
import {AddIcon} from '../../components/IconBundles';
import {FieldDefinitionDto, FieldLifecycleStatus, SchemaDefinitionDto} from '../../models/models';
import {
    createSchemaDraftVersion,
    getSchema,
    listFieldDefinitions,
    listSchemas,
    publishSchemaDraft,
    retireSchema,
} from '../../../services/fieldsService';
import {useFieldsTabStyles} from './FieldsTabStyles';
import SchemaEditorDialog from './SchemaEditorDialog';
import SchemaCard from './SchemaCard';

interface Props
{
    canManage: boolean;
}

const SchemasPanel = ({canManage}: Props) =>
{
    const styles = useFieldsTabStyles();
    const [schemas, setSchemas] = useState<SchemaDefinitionDto[]>([]);
    const [definitions, setDefinitions] = useState<FieldDefinitionDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [editorOpen, setEditorOpen] = useState(false);
    const [editing, setEditing] = useState<SchemaDefinitionDto | undefined>(undefined);

    const load = () =>
    {
        setLoading(true);
        setError(null);
        Promise.all([listSchemas(), listFieldDefinitions()])
            .then(([schemaList, definitionList]) => { setSchemas(schemaList); setDefinitions(definitionList); })
            .catch(() => setError('Failed to load schemas'))
            .finally(() => setLoading(false));
    };

    useEffect(() => { load(); }, []);

    const openCreate = () => { setEditing(undefined); setEditorOpen(true); };

    const openEdit = async (schema: SchemaDefinitionDto) =>
    {
        const fresh = schema.draftVersion ? schema : await getSchema(schema.id).catch(() => schema);
        setEditing(fresh);
        setEditorOpen(true);
    };

    const handlePublish = async (schema: SchemaDefinitionDto) =>
    {
        await publishSchemaDraft(schema.id).catch(() => null);
        load();
    };

    const handleNewVersion = async (schema: SchemaDefinitionDto) =>
    {
        await createSchemaDraftVersion(schema.id).catch(() => null);
        const fresh = await getSchema(schema.id).catch(() => undefined);
        if (fresh) { setEditing(fresh); setEditorOpen(true); }
        load();
    };

    const handleRetire = async (schema: SchemaDefinitionDto) =>
    {
        await retireSchema(schema.id).catch(() => null);
        load();
    };

    const activeSchemas = schemas.filter(s => s.status !== FieldLifecycleStatus.RETIRED);

    return (
        <div className={styles.container}>
            <div className={styles.headerRow}>
                <Text size={300}
                      className={styles.descriptionText}>
                    Business schemas group fields into a case type that exchange creators can select.
                </Text>
                {canManage && (
                    <Button id="schema-create-btn"
                            appearance="secondary"
                            shape="circular"
                            icon={<AddIcon/>}
                            onClick={openCreate}>
                        New schema
                    </Button>
                )}
            </div>

            {loading && <Spinner size="small" label="Loading..."/>}
            {!loading && error && <Text className={styles.errorText}>{error}</Text>}
            {!loading && !error && activeSchemas.length === 0 && (
                <Text className={styles.emptyText}>No schemas yet.</Text>
            )}

            <div className={styles.list}>
                {activeSchemas.map(schema => (
                    <SchemaCard key={schema.id}
                                schema={schema}
                                canManage={canManage}
                                onEdit={openEdit}
                                onPublish={handlePublish}
                                onNewVersion={handleNewVersion}
                                onRetire={handleRetire}/>
                ))}
            </div>

            <SchemaEditorDialog open={editorOpen}
                                definitions={definitions}
                                schema={editing}
                                onClose={() => setEditorOpen(false)}
                                onSaved={() => { setEditorOpen(false); load(); }}/>
        </div>
    );
};

export default SchemasPanel;
