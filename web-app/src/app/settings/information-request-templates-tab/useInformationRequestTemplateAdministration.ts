import {useCallback, useEffect, useState} from "react";
import {
    Capability,
    InformationRequestTemplateDto,
    InformationRequestTemplateScopeKind,
    InformationRequestTemplateSummaryDto,
    SchemaDefinitionDto,
} from "../../models/models.tsx";
import {useAuth} from "../../../context/AuthContext.tsx";
import {listSchemas} from "../../../services/fieldsService.ts";
import {
    createInformationRequestTemplate,
    getInformationRequestTemplate,
    listInformationRequestTemplates,
    publishInformationRequestTemplateDraft,
    replaceInformationRequestTemplateDraftConfiguration,
} from "../../../services/informationRequestTemplateService.ts";
import {
    DraftSaveRequest,
    fieldScopeFor,
    informationRequestTemplateErrorText,
    toConfigurationRequest,
} from "./informationRequestTemplateDraftUtils.ts";

interface TemplateAdministrationState
{
    hasOrg: boolean;
    scope: InformationRequestTemplateScopeKind | null;
    setScope: (scope: InformationRequestTemplateScopeKind) => void;
    canManageScope: boolean;
    templates: InformationRequestTemplateSummaryDto[];
    selectedTemplate: InformationRequestTemplateDto | null;
    schemas: SchemaDefinitionDto[];
    loading: boolean;
    error: string | null;
    createDraft: () => Promise<void>;
    openDraft: (template: InformationRequestTemplateSummaryDto) => Promise<void>;
    saveDraft: (template: InformationRequestTemplateDto, request: DraftSaveRequest) => Promise<void>;
    publishDraft: (template: InformationRequestTemplateDto) => Promise<void>;
}

export const useInformationRequestTemplateAdministration = (): TemplateAdministrationState =>
{
    const {currentSession, hasCapability} = useAuth();
    const activeOrganizationId = currentSession?.activeOrganizationId ?? null;
    const contextKey = currentSession === null ? null : activeOrganizationId ?? "PERSONAL";
    const defaultScope = activeOrganizationId
        ? InformationRequestTemplateScopeKind.ORGANIZATION
        : InformationRequestTemplateScopeKind.PERSONAL;
    const [scopeSelection, setScopeSelection] = useState({
        contextKey,
        scope: defaultScope,
    });
    const scope = contextKey === null
        ? null
        : scopeSelection.contextKey === contextKey
            ? scopeSelection.scope
            : defaultScope;
    const hasOrg = activeOrganizationId !== null;
    const canManageScope = scope === InformationRequestTemplateScopeKind.PERSONAL
        || scope === InformationRequestTemplateScopeKind.ORGANIZATION
        && hasCapability(Capability.ORG_POLICY_MANAGE);
    const [templates, setTemplates] = useState<InformationRequestTemplateSummaryDto[]>([]);
    const [selectedTemplate, setSelectedTemplate] = useState<InformationRequestTemplateDto | null>(null);
    const [schemas, setSchemas] = useState<SchemaDefinitionDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const loadTemplates = useCallback(async () =>
    {
        if (scope === null)
        {
            setTemplates([]);
            return;
        }
        setLoading(true);
        setError(null);
        try
        {
            setTemplates(await listInformationRequestTemplates({scopeKind: scope}));
        }
        catch
        {
            setError("Failed to load Information Request Templates");
        }
        finally
        {
            setLoading(false);
        }
    }, [scope]);

    const loadSchemas = useCallback(async () =>
    {
        if (scope === null || !canManageScope)
        {
            setSchemas([]);
            return;
        }
        try
        {
            setSchemas(await listSchemas({scopeKind: fieldScopeFor(scope)}));
        }
        catch
        {
            setSchemas([]);
        }
    }, [canManageScope, scope]);

    useEffect(() =>
    {
        setSelectedTemplate(null);
        void loadTemplates();
        void loadSchemas();
    }, [loadTemplates, loadSchemas]);

    const openDraft = async (template: InformationRequestTemplateSummaryDto) =>
    {
        setError(null);
        try
        {
            setSelectedTemplate(await getInformationRequestTemplate(template.id));
        }
        catch (caught: unknown)
        {
            setError(informationRequestTemplateErrorText(caught));
        }
    };

    const createDraft = async () =>
    {
        if (scope === null || !canManageScope) return;
        setError(null);
        try
        {
            const created = await createInformationRequestTemplate({
                namespace: "process",
                templateKey: `request-template-${Date.now()}`,
                displayName: "New Information Request Template",
                scopeKind: scope,
            });
            setSelectedTemplate(created);
            void loadTemplates();
        }
        catch (caught: unknown)
        {
            setError(informationRequestTemplateErrorText(caught));
        }
    };

    const saveDraft = async (template: InformationRequestTemplateDto, request: DraftSaveRequest) =>
    {
        setError(null);
        try
        {
            const updated = await replaceInformationRequestTemplateDraftConfiguration(template.id, toConfigurationRequest(request));
            setSelectedTemplate(updated);
            void loadTemplates();
        }
        catch (caught: unknown)
        {
            setError(informationRequestTemplateErrorText(caught));
        }
    };

    const publishDraft = async (template: InformationRequestTemplateDto) =>
    {
        setError(null);
        try
        {
            const updated = await publishInformationRequestTemplateDraft(template.id);
            setSelectedTemplate(updated);
            void loadTemplates();
        }
        catch (caught: unknown)
        {
            setError(informationRequestTemplateErrorText(caught));
        }
    };

    return {
        hasOrg,
        scope,
        setScope: nextScope => setScopeSelection({contextKey, scope: nextScope}),
        canManageScope,
        templates,
        selectedTemplate,
        schemas,
        loading,
        error,
        createDraft,
        openDraft,
        saveDraft,
        publishDraft,
    };
};
