import {FieldScopeKind, InformationRequestTemplateScopeKind, SchemaDefinitionDto} from "../../models/models.tsx";

export const fieldScopeFor = (scope: InformationRequestTemplateScopeKind): FieldScopeKind =>
{
    if (scope === InformationRequestTemplateScopeKind.ORGANIZATION) return FieldScopeKind.ORGANIZATION;
    if (scope === InformationRequestTemplateScopeKind.PLATFORM) return FieldScopeKind.PLATFORM;
    return FieldScopeKind.PERSONAL;
};

export const schemaScopesFor = (scope: InformationRequestTemplateScopeKind): FieldScopeKind[] =>
    scope === InformationRequestTemplateScopeKind.PLATFORM
        ? [FieldScopeKind.PLATFORM]
        : [fieldScopeFor(scope), FieldScopeKind.PLATFORM];

export const distinctSchemas = (lists: SchemaDefinitionDto[][]): SchemaDefinitionDto[] =>
{
    const seen = new Set<string>();
    return lists.flat().filter(schema =>
    {
        if (seen.has(schema.id)) return false;
        seen.add(schema.id);
        return true;
    });
};
