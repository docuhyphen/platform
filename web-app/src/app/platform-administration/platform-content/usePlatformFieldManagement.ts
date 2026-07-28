import {useCallback, useEffect, useState} from "react";
import {
    FieldDefinitionDto,
    FieldLifecycleStatus,
    SchemaDefinitionDto,
} from "../../models/models.tsx";
import {
    createPlatformSchemaDraftVersion,
    getPlatformSchema,
    listPlatformFields,
    listPlatformSchemas,
    publishPlatformSchema,
    retirePlatformField,
    retirePlatformSchema,
} from "../../../services/platformFieldsService.ts";

export const usePlatformFieldManagement = () =>
{
    const [fields, setFields] = useState<FieldDefinitionDto[]>([]);
    const [schemas, setSchemas] = useState<SchemaDefinitionDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [fieldEditorOpen, setFieldEditorOpen] = useState(false);
    const [schemaEditor, setSchemaEditor] = useState<SchemaDefinitionDto | "new" | null>(null);

    const load = useCallback(async () =>
    {
        setLoading(true);
        setError(null);
        try
        {
            const [fieldResult, schemaResult] = await Promise.all([
                listPlatformFields(),
                listPlatformSchemas(),
            ]);
            setFields(fieldResult.filter(field => field.status !== FieldLifecycleStatus.RETIRED));
            setSchemas(schemaResult.filter(schema => schema.status !== FieldLifecycleStatus.RETIRED));
        }
        catch (reason: unknown)
        {
            setError(reason instanceof Error ? reason.message : "Failed to load platform fields and schemas.");
        }
        finally
        {
            setLoading(false);
        }
    }, []);

    useEffect(() =>
    {
        void load();
    }, [load]);

    const mutate = async (action: () => Promise<unknown>) =>
    {
        setError(null);
        try
        {
            await action();
            await load();
        }
        catch
        {
            setError("The platform field configuration could not be updated.");
        }
    };

    const openSchema = async (schema: SchemaDefinitionDto) =>
    {
        setError(null);
        try
        {
            setSchemaEditor(await getPlatformSchema(schema));
        }
        catch
        {
            setError("The platform schema could not be opened.");
        }
    };

    const openSchemaVersion = async (schema: SchemaDefinitionDto) =>
    {
        setError(null);
        try
        {
            await createPlatformSchemaDraftVersion(schema);
            setSchemaEditor(await getPlatformSchema(schema));
            await load();
        }
        catch
        {
            setError("A new platform schema version could not be created.");
        }
    };

    const saved = () =>
    {
        setFieldEditorOpen(false);
        setSchemaEditor(null);
        void load();
    };

    return {
        fields,
        schemas,
        loading,
        error,
        fieldEditorOpen,
        schemaEditor,
        setFieldEditorOpen,
        setSchemaEditor,
        load,
        saved,
        openSchema,
        openSchemaVersion,
        retireField: (field: FieldDefinitionDto) => mutate(() => retirePlatformField(field)),
        publishSchema: (schema: SchemaDefinitionDto) => mutate(() => publishPlatformSchema(schema)),
        retireSchema: (schema: SchemaDefinitionDto) => mutate(() => retirePlatformSchema(schema)),
    };
};
