import {Dispatch, SetStateAction, useCallback, useEffect, useState} from "react";
import {TabValue} from "@fluentui/react-components";
import {
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
    scope: TabValue;
    setScope: Dispatch<SetStateAction<TabValue>>;
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
    const {appUserPersonOrganization} = useAuth();
    const hasOrg = appUserPersonOrganization?.isActive === true;
    const [scope, setScope] = useState<TabValue>(InformationRequestTemplateScopeKind.PERSONAL);
    const [templates, setTemplates] = useState<InformationRequestTemplateSummaryDto[]>([]);
    const [selectedTemplate, setSelectedTemplate] = useState<InformationRequestTemplateDto | null>(null);
    const [schemas, setSchemas] = useState<SchemaDefinitionDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const activeScope = scope as InformationRequestTemplateScopeKind;

    const loadTemplates = useCallback(async () =>
    {
        setLoading(true);
        setError(null);
        try
        {
            setTemplates(await listInformationRequestTemplates({scopeKind: activeScope}));
        }
        catch
        {
            setError("Failed to load Information Request Templates");
        }
        finally
        {
            setLoading(false);
        }
    }, [activeScope]);

    const loadSchemas = useCallback(async () =>
    {
        try
        {
            setSchemas(await listSchemas({scopeKind: fieldScopeFor(activeScope)}));
        }
        catch
        {
            setSchemas([]);
        }
    }, [activeScope]);

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
        setError(null);
        try
        {
            const created = await createInformationRequestTemplate({
                namespace: "process",
                templateKey: `request-template-${Date.now()}`,
                displayName: "New Information Request Template",
                scopeKind: activeScope,
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
        setScope,
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
