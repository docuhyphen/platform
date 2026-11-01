import {useEffect, useState} from 'react';
import {FieldDefinitionDto, SchemaDefinitionDto} from '../../../models/models';
import {
    createSchemaDraftVersion,
    getSchema,
    listFieldDefinitions,
    publishSchemaDraft,
    retireSchema,
} from '../../../../services/fieldsService';

export interface SchemaPanelActions
{
    definitions: FieldDefinitionDto[];
    editorOpen: boolean;
    editing: SchemaDefinitionDto | undefined;
    openCreate: () => void;
    openEdit: (schema: SchemaDefinitionDto) => Promise<void>;
    publish: (schema: SchemaDefinitionDto) => Promise<void>;
    newVersion: (schema: SchemaDefinitionDto) => Promise<void>;
    retire: (schema: SchemaDefinitionDto) => Promise<void>;
    closeEditor: () => void;
    editorSaved: () => void;
}

export const useSchemaPanelActions = (onRefresh: () => void): SchemaPanelActions =>
{
    const [definitions, setDefinitions] = useState<FieldDefinitionDto[]>([]);
    const [editorOpen, setEditorOpen] = useState(false);
    const [editing, setEditing] = useState<SchemaDefinitionDto | undefined>(undefined);

    useEffect(() =>
    {
        listFieldDefinitions().then(setDefinitions).catch(() => null);
    }, []);

    const openCreate = () => { setEditing(undefined); setEditorOpen(true); };

    const openEdit = async (schema: SchemaDefinitionDto) =>
    {
        const fresh = schema.draftVersion ? schema : await getSchema(schema.id).catch(() => schema);
        setEditing(fresh);
        setEditorOpen(true);
    };

    const publish = async (schema: SchemaDefinitionDto) =>
    {
        await publishSchemaDraft(schema.id).catch(() => null);
        onRefresh();
    };

    const newVersion = async (schema: SchemaDefinitionDto) =>
    {
        await createSchemaDraftVersion(schema.id).catch(() => null);
        const fresh = await getSchema(schema.id).catch(() => undefined);
        if (fresh) { setEditing(fresh); setEditorOpen(true); }
        onRefresh();
    };

    const retire = async (schema: SchemaDefinitionDto) =>
    {
        await retireSchema(schema.id).catch(() => null);
        onRefresh();
    };

    const closeEditor = () => setEditorOpen(false);

    const editorSaved = () => { setEditorOpen(false); onRefresh(); };

    return {definitions, editorOpen, editing, openCreate, openEdit, publish, newVersion, retire, closeEditor, editorSaved};
};
