import {useCallback, useEffect, useMemo, useState} from "react";
import {
    Capability,
    CreateInformationRequestTemplateRequest,
    InformationRequestTemplateDto,
    InformationRequestTemplateScopeKind,
    InformationRequestTemplateSummaryDto,
    SchemaDefinitionDto,
} from "../../models/models.tsx";
import {useAuth} from "../../../context/AuthContext.tsx";
import {listSchemas} from "../../../services/fieldsService.ts";
import {
    cloneInformationRequestTemplate,
    createInformationRequestTemplate,
    createInformationRequestTemplateDraftVersion,
    getInformationRequestTemplate,
    listInformationRequestTemplates,
    publishInformationRequestTemplateDraft,
    replaceInformationRequestTemplateDraftConfiguration,
    retireInformationRequestTemplateVersion,
} from "../../../services/informationRequestTemplateService.ts";
import {TemplateEditorCommands} from "./template-editor/templateEditorTypes.ts";
import {distinctSchemas, schemaScopesFor} from "./informationRequestTemplateScope.ts";

const LIST_FAILURE = "Information Request Templates could not be loaded.";

const refusalText = (caught: unknown, fallback: string): string =>
{
    if (typeof caught === "string" && caught) return caught;
    if (typeof caught === "object" && caught !== null && "errorMessage" in caught)
    {
        const message = (caught as {errorMessage?: unknown}).errorMessage;
        if (typeof message === "string" && message) return message;
    }
    return fallback;
};

export const useInformationRequestTemplateAdministration = () =>
{
    const {currentSession, hasCapability} = useAuth();
    const activeOrganizationId = currentSession?.activeOrganizationId ?? null;
    const contextKey = currentSession === null ? null : activeOrganizationId ?? "PERSONAL";
    const defaultScope = activeOrganizationId
        ? InformationRequestTemplateScopeKind.ORGANIZATION
        : InformationRequestTemplateScopeKind.PERSONAL;
    const [scopeSelection, setScopeSelection] = useState({contextKey, scope: defaultScope});
    const scope = contextKey === null ? null : scopeSelection.contextKey === contextKey ? scopeSelection.scope : defaultScope;
    const canManageScope = scope === InformationRequestTemplateScopeKind.PERSONAL
        || (scope === InformationRequestTemplateScopeKind.ORGANIZATION && hasCapability(Capability.ORG_POLICY_MANAGE));
    const [templates, setTemplates] = useState<InformationRequestTemplateSummaryDto[]>([]);
    const [openTemplate, setOpenTemplate] = useState<InformationRequestTemplateDto | null>(null);
    const [schemas, setSchemas] = useState<SchemaDefinitionDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const loadTemplates = useCallback(async () =>
    {
        if (scope === null) return;
        setLoading(true);
        setError(null);
        try
        {
            setTemplates(await listInformationRequestTemplates({scopeKind: scope}));
        }
        catch
        {
            setTemplates([]);
            setError(LIST_FAILURE);
        }
        finally
        {
            setLoading(false);
        }
    }, [scope]);

    useEffect(() =>
    {
        setOpenTemplate(null);
        void loadTemplates();
        if (scope === null) return;
        Promise.all(schemaScopesFor(scope).map(scopeKind => listSchemas({scopeKind}).catch(() => [])))
            .then(lists => setSchemas(distinctSchemas(lists)));
    }, [loadTemplates, scope]);

    const opened = useCallback((template: InformationRequestTemplateDto): InformationRequestTemplateDto =>
    {
        setOpenTemplate(template);
        void loadTemplates();
        return template;
    }, [loadTemplates]);

    const openTemplateId = openTemplate?.id;
    const commands = useMemo<TemplateEditorCommands | null>(() =>
    {
        if (!openTemplateId) return null;
        const id = openTemplateId;
        return {
            save: async configuration => opened(await replaceInformationRequestTemplateDraftConfiguration(id, configuration)),
            publish: async () => opened(await publishInformationRequestTemplateDraft(id)),
            startDraft: async versionNumber =>
                opened(await createInformationRequestTemplateDraftVersion(id, {sourceVersionNumber: versionNumber})),
            retire: async versionNumber => opened(await retireInformationRequestTemplateVersion(id, versionNumber)),
            clone: async (versionNumber, target: CreateInformationRequestTemplateRequest) =>
                opened(await cloneInformationRequestTemplate(id, {sourceVersionNumber: versionNumber, target})),
        };
    }, [openTemplateId, opened]);

    return {
        hasOrg: activeOrganizationId !== null,
        scope,
        setScope: (next: InformationRequestTemplateScopeKind) => setScopeSelection({contextKey, scope: next}),
        canManageScope,
        templates,
        loading,
        error,
        schemas,
        openTemplate,
        commands,
        open: async (summary: InformationRequestTemplateSummaryDto) =>
        {
            setError(null);
            try
            {
                setOpenTemplate(await getInformationRequestTemplate(summary.id));
            }
            catch
            {
                setError("The Template could not be opened.");
            }
        },
        close: () => setOpenTemplate(null),
        create: async (request: CreateInformationRequestTemplateRequest): Promise<string | null> =>
        {
            try
            {
                opened(await createInformationRequestTemplate(request));
                return null;
            }
            catch (caught: unknown)
            {
                return refusalText(caught, "The Template could not be created.");
            }
        },
    };
};
