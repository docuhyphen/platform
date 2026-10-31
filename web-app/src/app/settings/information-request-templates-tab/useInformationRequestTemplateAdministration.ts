import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {
    Capability,
    CreateInformationRequestTemplateRequest,
    InformationRequestCapabilitiesDto,
    InformationRequestStandingReason,
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
} from "../../../services/informationRequestTemplateService.ts";
import {normalizeApiError} from "../../../utils/apiErrorUtils.ts";
import {useServerRead} from "../../information-requests/shared/useServerRead.ts";
import {useStableIdempotencyKey} from "../../information-requests/shared/useStableIdempotencyKey.ts";
import {fieldScopeFor} from "./informationRequestTemplateScope.ts";
import {useTemplateEditorCommands} from "./useTemplateEditorCommands.ts";

const LIST_FAILURE = "Information Request Templates could not be loaded.";

const SCHEMA_FAILURE = "The Schemas this scope can use could not be loaded.";

const NO_SCHEMAS: SchemaDefinitionDto[] = [];

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

const statedRefusal = (caught: unknown): string | null => {
    const refusal = normalizeApiError(caught, LIST_FAILURE);
    return refusal.reasonCode && refusal.retryAfterSeconds === undefined ? refusal.message : null;
};

const templateWorkAvailable = (capabilities: InformationRequestCapabilitiesDto | null): boolean =>
    capabilities === null
    || capabilities.newWorkAvailable
    || capabilities.newWorkUnavailableReason === InformationRequestStandingReason.OPEN_REQUEST_LIMIT_REACHED;

export const useInformationRequestTemplateAdministration = (capabilities: InformationRequestCapabilitiesDto | null) =>
{
    const {currentSession, hasCapability} = useAuth();
    const activeOrganizationId = currentSession?.activeOrganizationId ?? null;
    const contextKey = currentSession === null ? null : activeOrganizationId ?? "PERSONAL";
    const defaultScope = activeOrganizationId
        ? InformationRequestTemplateScopeKind.ORGANIZATION
        : InformationRequestTemplateScopeKind.PERSONAL;
    const [scopeSelection, setScopeSelection] = useState({contextKey, scope: defaultScope});
    const scope = contextKey === null ? null : scopeSelection.contextKey === contextKey ? scopeSelection.scope : defaultScope;
    const platformAuthor = hasCapability(Capability.APP_ADMIN);
    const canManageScope = scope === InformationRequestTemplateScopeKind.PERSONAL
        ? capabilities?.personalTemplatesAvailable ?? true
        : scope === InformationRequestTemplateScopeKind.PLATFORM
            ? platformAuthor
            : scope === InformationRequestTemplateScopeKind.ORGANIZATION
            && hasCapability(Capability.ORG_POLICY_MANAGE)
            && templateWorkAvailable(capabilities);
    const copyTargets = useMemo<InformationRequestTemplateScopeKind[]>(() => [
        ...(capabilities?.personalTemplatesAvailable ?? true ? [InformationRequestTemplateScopeKind.PERSONAL] : []),
        ...(activeOrganizationId !== null && hasCapability(Capability.ORG_POLICY_MANAGE) && templateWorkAvailable(capabilities)
            ? [InformationRequestTemplateScopeKind.ORGANIZATION]
            : []),
        ...(platformAuthor ? [InformationRequestTemplateScopeKind.PLATFORM] : []),
    ], [activeOrganizationId, capabilities, hasCapability, platformAuthor]);
    const [templates, setTemplates] = useState<InformationRequestTemplateSummaryDto[]>([]);
    const [openTemplate, setOpenTemplate] = useState<InformationRequestTemplateDto | null>(null);
    const [loading, setLoading] = useState(false);
    const [listError, setListError] = useState<string | null>(null);
    const [listRefusal, setListRefusal] = useState<string | null>(null);
    const [openError, setOpenError] = useState<string | null>(null);
    const schemaRead = useServerRead(
        `${scope ?? ""}:${canManageScope}`,
        () => scope === null || !canManageScope
            ? Promise.resolve(NO_SCHEMAS)
            : listSchemas({scopeKind: fieldScopeFor(scope)}),
        () => SCHEMA_FAILURE,
    );
    const draftETag = useRef<string | undefined>(undefined);
    const latestListRead = useRef(0);
    const keyed = useStableIdempotencyKey();

    const loadTemplates = useCallback(async () =>
    {
        if (scope === null) return;
        const read = ++latestListRead.current;
        setLoading(true);
        setListError(null);
        setListRefusal(null);
        try
        {
            const listed = await listInformationRequestTemplates({scopeKind: scope});
            if (read === latestListRead.current) setTemplates(listed);
        } catch (caught: unknown)
        {
            if (read === latestListRead.current) {
                const refusal = statedRefusal(caught);
                setTemplates([]);
                setListRefusal(refusal);
                setListError(refusal ? null : LIST_FAILURE);
            }
        }
        finally
        {
            if (read === latestListRead.current) setLoading(false);
        }
    }, [scope]);

    useEffect(() =>
    {
        draftETag.current = undefined;
        setOpenTemplate(null);
        setOpenError(null);
        setTemplates([]);
        void loadTemplates();
    }, [loadTemplates]);

    const show = useCallback((template: InformationRequestTemplateDto | null) =>
    {
        draftETag.current = template?.draftETag ?? undefined;
        setOpenTemplate(template);
    }, []);

    const opened = useCallback((template: InformationRequestTemplateDto): InformationRequestTemplateDto => {
        show(template);
        void loadTemplates();
        return template;
    }, [loadTemplates, show]);

    const commands = useTemplateEditorCommands(openTemplate?.id, draftETag, keyed, opened);

    return {
        hasOrg: activeOrganizationId !== null,
        scope,
        setScope: (next: InformationRequestTemplateScopeKind) => setScopeSelection({contextKey, scope: next}),
        canManageScope,
        copyTargets,
        templates,
        loading,
        listError,
        listRefusal,
        openError,
        reloadTemplates: loadTemplates,
        schemas: schemaRead.value ?? NO_SCHEMAS,
        schemasError: schemaRead.error,
        reloadSchemas: schemaRead.reload,
        openTemplate,
        commands,
        open: async (summary: InformationRequestTemplateSummaryDto) =>
        {
            setOpenError(null);
            try
            {
                show(await getInformationRequestTemplate(summary.id));
            }
            catch
            {
                setOpenError("The Template could not be opened.");
            }
        },
        close: () => show(null),
        create: async (request: CreateInformationRequestTemplateRequest): Promise<string | null> =>
        {
            try
            {
                opened(await keyed("create", request, key => createInformationRequestTemplate(request, key)));
                return null;
            }
            catch (caught: unknown)
            {
                return refusalText(caught, "The Template could not be created.");
            }
        },
    };
};
