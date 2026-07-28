import {
    FieldDefinitionDto,
    FieldScopeKind,
    SchemaDefinitionDto,
    SchemaVersionDto,
} from "../app/models/models.tsx";
import {
    BindingRequest,
    CreateFieldDefinitionRequest,
    CreateSchemaRequest,
    createFieldDefinition,
    createSchema,
    createSchemaDraftVersion,
    getSchema,
    listFieldDefinitions,
    listSchemas,
    publishSchemaDraft,
    retireFieldDefinition,
    retireSchema,
    updateSchemaDraftBindings,
} from "./fieldsService.ts";

const requirePlatformField = (definition: FieldDefinitionDto): FieldDefinitionDto =>
{
    if (definition.scopeKind !== FieldScopeKind.PLATFORM)
        throw new Error("Platform Administration can manage only PLATFORM-scoped fields.");
    return definition;
};

const requirePlatformSchema = (schema: SchemaDefinitionDto): SchemaDefinitionDto =>
{
    if (schema.scopeKind !== FieldScopeKind.PLATFORM)
        throw new Error("Platform Administration can manage only PLATFORM-scoped schemas.");
    return schema;
};

export const listPlatformFields = async (): Promise<FieldDefinitionDto[]> =>
{
    const definitions = await listFieldDefinitions({scopeKind: FieldScopeKind.PLATFORM});
    definitions.forEach(requirePlatformField);
    return definitions;
};

export const createPlatformField = async (
    request: Omit<CreateFieldDefinitionRequest, "scopeKind">,
): Promise<FieldDefinitionDto> =>
    requirePlatformField(await createFieldDefinition({
        ...request,
        scopeKind: FieldScopeKind.PLATFORM,
    }));

export const retirePlatformField = async (
    definition: FieldDefinitionDto,
): Promise<FieldDefinitionDto> =>
{
    requirePlatformField(definition);
    return requirePlatformField(await retireFieldDefinition(definition.id));
};

export const listPlatformSchemas = async (): Promise<SchemaDefinitionDto[]> =>
{
    const schemas = await listSchemas({scopeKind: FieldScopeKind.PLATFORM});
    schemas.forEach(requirePlatformSchema);
    return schemas;
};

export const getPlatformSchema = async (
    schema: SchemaDefinitionDto,
): Promise<SchemaDefinitionDto> =>
{
    requirePlatformSchema(schema);
    return requirePlatformSchema(await getSchema(schema.id));
};

export const createPlatformSchema = async (
    request: Omit<CreateSchemaRequest, "scopeKind">,
): Promise<SchemaDefinitionDto> =>
    requirePlatformSchema(await createSchema({
        ...request,
        scopeKind: FieldScopeKind.PLATFORM,
    }));

export const updatePlatformSchemaBindings = (
    schema: SchemaDefinitionDto,
    bindings: BindingRequest[],
): Promise<SchemaVersionDto> =>
{
    requirePlatformSchema(schema);
    return updateSchemaDraftBindings(schema.id, {bindings});
};

export const publishPlatformSchema = async (
    schema: SchemaDefinitionDto,
): Promise<SchemaDefinitionDto> =>
{
    requirePlatformSchema(schema);
    return requirePlatformSchema(await publishSchemaDraft(schema.id));
};

export const createPlatformSchemaDraftVersion = (
    schema: SchemaDefinitionDto,
): Promise<SchemaVersionDto> =>
{
    requirePlatformSchema(schema);
    return createSchemaDraftVersion(schema.id);
};

export const retirePlatformSchema = async (
    schema: SchemaDefinitionDto,
): Promise<SchemaDefinitionDto> =>
{
    requirePlatformSchema(schema);
    return requirePlatformSchema(await retireSchema(schema.id));
};
