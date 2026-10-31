import {FieldScopeKind, SchemaDefinitionDto} from '../../models/models';
import {createSchema, updateSchemaDraftBindings} from '../../../services/fieldsService';
import {createPlatformSchema, updatePlatformSchemaBindings} from '../../../services/platformFieldsService.ts';
import {BindingDraft, toBindingRequests} from './schemaBindingDrafts';
import {SchemaIdentity} from './SchemaIdentityFields';

export const saveSchema = async (
    identity: SchemaIdentity,
    bindings: BindingDraft[],
    schema: SchemaDefinitionDto | undefined,
    enforcedScope: FieldScopeKind | undefined,
): Promise<void> =>
{
    if (schema)
    {
        const requests = toBindingRequests(bindings);
        if (enforcedScope === FieldScopeKind.PLATFORM)
            await updatePlatformSchemaBindings(schema, requests);
        else
            await updateSchemaDraftBindings(schema.id, {bindings: requests});
        return;
    }
    const request = {
        namespace: identity.namespace.trim(),
        schemaKey: identity.schemaKey.trim(),
        displayName: identity.displayName.trim(),
        description: identity.description.trim() || undefined,
        targetResourceType: identity.targetResourceType,
        bindings: toBindingRequests(bindings),
    };
    if (enforcedScope === FieldScopeKind.PLATFORM)
        await createPlatformSchema(request);
    else
        await createSchema(request);
};
