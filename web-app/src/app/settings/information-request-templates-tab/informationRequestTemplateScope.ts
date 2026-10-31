import {FieldScopeKind, InformationRequestTemplateScopeKind} from "../../models/models.tsx";

export const fieldScopeFor = (scope: InformationRequestTemplateScopeKind): FieldScopeKind =>
{
    if (scope === InformationRequestTemplateScopeKind.ORGANIZATION) return FieldScopeKind.ORGANIZATION;
    if (scope === InformationRequestTemplateScopeKind.PLATFORM) return FieldScopeKind.PLATFORM;
    return FieldScopeKind.PERSONAL;
};
