import React from 'react';
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Link,
    mergeClasses,
    MessageBar,
    MessageBarActions,
    MessageBarBody,
    MessageBarGroup,
    Text,
    Toast,
    Toaster,
    ToastTitle,
    ToastTrigger,
    useId,
    useToastController,
} from "@fluentui/react-components";
import {initiateExchange} from "../../services/exchangeApi.ts";
import useExchangeInitiatingState from './hooks/useExchangeInitiatingState.ts';
import {handleCheckboxChange, handleDocumentChange, handleInputChange} from './formHandlers.tsx';
import SharingDocumentsTab from "./components/exchange-initiation-documents-tab/ExchangeInitiationDocumentsTab.tsx";
import ExchangeInitiationDetailsTab
    from "./components/exchange-initiation-details-tab/ExchangeInitiationDetailsTab.tsx";
import SharingOptionsTab from "./components/exchange-initiation-options-tab/ExchangeInitiationOptionsTab.tsx";
import ExchangeInitiationDialogActions
    from "./components/exchange-initiation-dialog-actions/ExchangeInitiationDialogActions.tsx";
import ExchangeInitiationDialogTrigger
    from "./components/exchange-initiation-dialog-trigger/ExchangeInitiationDialogTrigger.tsx";
import ExchangeInitiationDialogTitleSection
    from "./components/exchange-initiation-dialog-title-section/ExchangeInitiationDialogTitleSection.tsx";
import ExchangeInitiationFieldsTab from "./components/exchange-initiation-fields-tab/ExchangeInitiationFieldsTab.tsx";
import {ArrowLeftRegular, DismissRegular} from "@fluentui/react-icons";
import ExchangeInitiationRecipientsTab
    from "./components/exchange-initiation-recipients-tab/ExchangeInitiationRecipientsTab.tsx";
import {
    ExchangeInitiationRecipientMode
} from "./components/exchange-initiation-recipients-tab/exchangeInitiationRecipientMode.ts";
import {publishNewExchangeAddition, recreateRejectedExchangeObservable} from '../observable/exchangeObservables.ts';
import {useExchangeInitiationStyles} from "./ExchangeInitiationStyles.tsx";
import {
    AvailableVariablesDto,
    BlueprintConfig,
    BlueprintDefinitionSummaryDto,
    BlueprintDocumentConfig,
    DocumentLibraryEntrySummaryDto,
    DocumentType,
    ExchangeInitiationRequest,
    ExchangeRequestDocumentRequest,
    PlanFeature,
    SchemaAssignmentSource,
    SchemaDefinitionDto
} from "../models/models.tsx";
import {getAvailableVariables} from "../../services/variableService.ts";
import {getResolvedSchema, listSchemas} from "../../services/fieldsService.ts";
import {
    buildBlueprintFieldDefaults,
    buildCreationFieldValues,
    filterEligibleExchangeSchemas
} from "./components/exchange-initiation-fields-tab/creationFieldsUtils.ts";
import BlueprintPicker from "./components/blueprint-picker/BlueprintPicker.tsx";
import SaveBlueprintPanel from "./components/save-blueprint-dialog/SaveBlueprintDialog.tsx";
import {useAuth} from "../../context/AuthContext.tsx";
import {useNavigate} from "react-router-dom";
import {buildRecipientSelection} from "./exchangeInitiationRecipientSelection.ts";
import {ExchangeShareRoleName} from "../../services/types/roles.ts";
import {fetchMyOrganizationUsers} from "../../services/organizationApi.ts";
import {blueprintParticipantsOf, participantUsersOf} from "./blueprintParticipants.ts";
import {usePlanFeature} from "../../hooks/subscription/usePlanFeature.ts";
import useSelectedBlueprint from "./hooks/useSelectedBlueprint.ts";
import {exchangeInitiationDocumentProblem} from "./exchangeInitiationDocuments.ts";
import {exchangeInitiationRefusalMessage} from "./exchangeInitiationRefusal.ts";
import {findStartedInformationRequest, StartedInformationRequest} from "./startedInformationRequest.ts";
import CreatedInformationRequestSummary
    from "./components/created-information-request-summary/CreatedInformationRequestSummary.tsx";
import {informationRequestManagePath} from "../information-requests/shared/informationRequestWorkspacePaths.ts";

type CreatedExchangeSummary = {
    id?: string;
    name: string;
    recipient: string;
    documents: number;
    requiresSignIn: boolean;
    shareLink?: string;
    informationRequest?: StartedInformationRequest | null;
};
type ExchangeInitiationTransitionDirection = "forward" | "back" | null;

const ExchangeInitiation: React.FC = () =>
{
    const styles = useExchangeInitiationStyles();
    const {appUser, refreshCurrentSession} = useAuth();
    const blueprintAvailability = usePlanFeature(PlanFeature.BLUEPRINT_USE);
    const blueprintManagementAvailability = usePlanFeature(PlanFeature.BLUEPRINT_MANAGE);
    const documentLibraryAvailability = usePlanFeature(PlanFeature.DOCUMENT_LIBRARY_USE);
    const participantAvailability = usePlanFeature(PlanFeature.MULTIPLE_PARTICIPANTS);
    const businessFieldsAvailability = usePlanFeature(PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS);
    const advancedAccessAvailability = usePlanFeature(PlanFeature.ADVANCED_ACCESS_CONTROLS);
    const navigate = useNavigate();
    const {
        choosingBlueprint, setChoosingBlueprint,
        name, setExchangeName,
        description, setDescription,
        initialShareMessage, setInitialShareMessage,
        requireSignIn, setRequireSignIn,
        allowDocumentAdditions, setAllowDocumentAdditions,
        allowDocumentDeletions, setAllowDocumentDeletions,
        allowDocumentDownload, setAllowDocumentDownload,
        allowDocumentUpdate, setAllowDocumentUpdate,
        allowDocumentUpload, setAllowDocumentUpload,
        initiatingExchange, setInitiatingExchange,
        exchangeInitiatedSuccessfully, setExchangeInitiatedSuccessfully,
        documents, setDocuments,
        selectedTab, setSelectedTab,
        messageGroupMessages, setMessageGroupMessages,
        requestingDocuments, setRequestingDocuments,
        recipientMode, setRecipientMode,
        recipientOrg, setRecipientOrg,
        recipientOrgUser, setRecipientOrgUser,
        recipientOrgGroup, setRecipientOrgGroup,
        recipientResolution, setRecipientResolution,
        internalParticipants, setInternalParticipants,
        newRecipient, setNewRecipient,
        recipientRole, setRecipientRole,
        recipientConstraints, setRecipientConstraints,
        allowedDownloadFormats, setAllowedDownloadFormats,
        schemaDefinitionId, setSchemaDefinitionId,
        fieldValueMap, setFieldValueMap,
        fieldBindings, setFieldBindings,
    } = useExchangeInitiatingState();

    const toasterId = useId("exchange-initiation-toaster");
    const [isDialogOpen, setIsDialogOpen] = React.useState(false);
    const {selectedBlueprint, selectBlueprint, clearBlueprint, startsInformationRequest} = useSelectedBlueprint();
    const [blueprintLocked, setBlueprintLocked] = React.useState(false);
    const [eligibleSchemas, setEligibleSchemas] = React.useState<SchemaDefinitionDto[]>([]);
    const [schemaFromBlueprint, setSchemaFromBlueprint] = React.useState(false);
    const [createdExchangeSummary, setCreatedExchangeSummary] = React.useState<CreatedExchangeSummary | null>(null);
    const [copyLinkStatus, setCopyLinkStatus] = React.useState<'idle' | 'copied' | 'failed'>('idle');
    const [saveBlueprintDialogOpen, setSaveBlueprintDialogOpen] = React.useState(false);
    const [availableVariables, setAvailableVariables] = React.useState<AvailableVariablesDto | null>(null);
    const [variableOverrides, setVariableOverrides] = React.useState<Record<string, string>>({});
    const [pendingVariableTokens, setPendingVariableTokens] = React.useState<string[]>([]);
    const appliedBlueprintId = React.useRef<string | null>(null);
    const [contentTransitionDirection, setContentTransitionDirection] =
        React.useState<ExchangeInitiationTransitionDirection>(null);

    const clearAppliedBlueprint = React.useCallback(() => {
        appliedBlueprintId.current = null;
        clearBlueprint();
        setBlueprintLocked(false);
        setInternalParticipants([]);
        setSchemaDefinitionId(undefined);
        setFieldValueMap({});
        setFieldBindings([]);
        setSchemaFromBlueprint(false);
        setVariableOverrides({});
        setPendingVariableTokens([]);
    }, [clearBlueprint, setFieldBindings, setFieldValueMap, setInternalParticipants, setSchemaDefinitionId]);

    const {dispatchToast} = useToastController(toasterId);

    React.useEffect(() =>
    {
        if (!participantAvailability.isAvailable && internalParticipants.length > 0)
        {
            setInternalParticipants([]);
        }
    }, [internalParticipants.length, participantAvailability.isAvailable, setInternalParticipants]);

    const openBlueprintPicker = () =>
    {
        if (!blueprintAvailability.isAvailable) return;
        setContentTransitionDirection("forward");
        setChoosingBlueprint(true);
    };

    const closeBlueprintPicker = () =>
    {
        setContentTransitionDirection("back");
        setChoosingBlueprint(false);
    };

    const openSaveBlueprintPanel = () =>
    {
        setContentTransitionDirection("forward");
        setSaveBlueprintDialogOpen(true);
    };

    const closeSaveBlueprintPanel = () =>
    {
        setContentTransitionDirection("back");
        setSaveBlueprintDialogOpen(false);
    };

    const showServerErrorToast = (message: string) =>
    {
        dispatchToast(
            <Toast id={"exchange-initiation-toast"}>
                <ToastTitle id={"exchange-initiation-message-toast-title"}
                            action={
                                <ToastTrigger>
                                    <Link id={"exchange-initiation-dismiss-link"}>Dismiss</Link>
                                </ToastTrigger>
                            }>
                    {message}
                </ToastTitle>
            </Toast>, {intent: 'error', timeout: 15000},
        );
    };

    const applyModeDefaults = (isRequesting: boolean) =>
    {
        if (isRequesting)
        {
            setAllowDocumentUpload(true);
            setAllowDocumentAdditions(true);
            setAllowDocumentDownload(false);
            setAllowDocumentUpdate(false);
            setAllowDocumentDeletions(false);
        }
        else
        {
            setAllowDocumentDownload(true);
            setAllowDocumentUpload(false);
            setAllowDocumentAdditions(false);
            setAllowDocumentUpdate(false);
            setAllowDocumentDeletions(false);
        }
    };

    const handleRequestingDocumentsChange = (isRequesting: boolean) =>
    {
        setRequestingDocuments(isRequesting);
        applyModeDefaults(isRequesting);
    };

    const applyBlueprintSchema = (blueprint: BlueprintDefinitionSummaryDto) =>
    {
        if (!blueprint.schemaDefinitionId)
        {
            setSchemaFromBlueprint(false);
            setSchemaDefinitionId(undefined);
            setFieldValueMap({});
            setFieldBindings([]);
            return;
        }
        const schemaId = blueprint.schemaDefinitionId;
        setSchemaFromBlueprint(true);
        setSchemaDefinitionId(schemaId);
        getResolvedSchema(schemaId)
            .then(view =>
            {
                if (appliedBlueprintId.current !== blueprint.id) return;
                setFieldBindings(view.fields);
                const contractByFieldDefinition = new Map(
                    view.fields.map(f => [f.fieldDefinitionId, f.fieldContractId]),
                );
                const seeded: Record<string, unknown> = {};
                (blueprint.fieldDefaults ?? []).forEach(d =>
                {
                    const contractId = contractByFieldDefinition.get(d.fieldDefinitionId);
                    if (contractId && d.value !== undefined && d.value !== null) seeded[contractId] = d.value;
                });
                setFieldValueMap(seeded);
            })
            .catch(() =>
            {
                if (appliedBlueprintId.current !== blueprint.id) return;
                setFieldBindings([]);
                setFieldValueMap({});
            });
    };

    const handleBlueprintSelect = (blueprint: BlueprintDefinitionSummaryDto) =>
    {
        appliedBlueprintId.current = blueprint.id;
        let config: BlueprintConfig | null = null;
        try
        {
            config = JSON.parse(blueprint.configJson) as BlueprintConfig;
            setExchangeName(config.name || blueprint.name);
            setDescription(config.description || blueprint.summary || '');
            if (config.initialShareMessage) setInitialShareMessage(config.initialShareMessage);
            if (config.requestRecipientSignIn !== undefined) setRequireSignIn(config.requestRecipientSignIn);
            if (config.allowDocumentAddition !== undefined) setAllowDocumentAdditions(config.allowDocumentAddition);
            if (config.allowDocumentDeletion !== undefined) setAllowDocumentDeletions(config.allowDocumentDeletion);
            if (config.allowDocumentDownload !== undefined) setAllowDocumentDownload(config.allowDocumentDownload);
            if (config.allowDocumentUpdate !== undefined) setAllowDocumentUpdate(config.allowDocumentUpdate);
            if (config.allowDocumentUpload !== undefined) setAllowDocumentUpload(config.allowDocumentUpload);
            if (config.allowedDownloadFormats !== undefined) setAllowedDownloadFormats(config.allowedDownloadFormats);
            if (blueprint.exchangeDocuments && blueprint.exchangeDocuments.length > 0)
            {
                const docs: ExchangeRequestDocumentRequest[] = blueprint.exchangeDocuments.map(d => ({
                    title: d.title,
                    restrictedType: d.restrictedType as ExchangeRequestDocumentRequest["restrictedType"],
                    restrictType: d.restrictType ?? false,
                    required: d.required ?? false,
                    libraryDocumentId: d.libraryDocumentId,
                }));
                setDocuments(docs);
            }
            const participantDefaults = blueprint.participants ?? [];
            if (participantDefaults.length > 0)
            {
                void fetchMyOrganizationUsers()
                    .then(users => {
                        if (appliedBlueprintId.current !== blueprint.id) return;
                        setInternalParticipants(participantUsersOf(participantDefaults, users));
                    })
                    .catch(() => undefined);
            }
            setBlueprintLocked(blueprint.scope !== 'PERSONAL' && !(config.allowEditOnExchangeStart === true));
            applyBlueprintSchema(blueprint);
        }
        catch
        {
            // Invalid configJson; apply what we can, ignore the rest
        }
        selectBlueprint(blueprint);
        setContentTransitionDirection("forward");
        setChoosingBlueprint(false);
        setSelectedTab('recipients-tab');

        if (availableVariables)
        {
            const systemTokenSet = new Set(availableVariables.system.map(s => s.token));
            const seqTokenSet = new Set(availableVariables.sequences.map(s => `SEQ:${s.key}`));
            const allStrings = [
                config?.name ?? '',
                config?.description ?? '',
                config?.initialShareMessage ?? '',
                ...(blueprint.exchangeDocuments ?? []).map(d => d.title),
            ];
            const found = new Set<string>();
            for (const s of allStrings)
            {
                const matches = s.matchAll(/\{\{([^}]+)}}/g);
                for (const m of matches)
                {
                    const t = m[1].trim();
                    if (!systemTokenSet.has(t) && !seqTokenSet.has(t)) found.add(t);
                }
            }
            const tokens = Array.from(found);
            if (tokens.length > 0)
            {
                const defaults: Record<string, string> = {};
                for (const t of tokens)
                {
                    const orgVar = availableVariables.org.find(v => v.key === t);
                    const persVar = availableVariables.personal.find(v => v.key === t);
                    defaults[t] = orgVar?.defaultValue ?? persVar?.defaultValue ?? '';
                }
                setVariableOverrides(defaults);
                setPendingVariableTokens(tokens);
            }
        }
    };

    const buildBlueprintConfigJson = (): string =>
    {
        const config: BlueprintConfig = {
            name,
            description,
            initialShareMessage,
            requestRecipientSignIn: requireSignIn,
            allowDocumentAddition: allowDocumentAdditions,
            allowDocumentDeletion: allowDocumentDeletions,
            allowDocumentDownload: allowDocumentDownload,
            allowDocumentUpdate: allowDocumentUpdate,
            allowDocumentUpload: allowDocumentUpload,
            allowedDownloadFormats: allowedDownloadFormats,
        };
        return JSON.stringify(config);
    };

    const buildBlueprintDocuments = (): BlueprintDocumentConfig[] =>
        documents.map(d => ({
            title: d.title,
            restrictedType: d.restrictedType as string | undefined,
            restrictType: d.restrictType,
            required: d.required,
            libraryDocumentId: d.libraryDocumentId,
        }));

    const buildRecipientLabel = (): string =>
    {
        if (recipientResolution)
        {
            return recipientResolution.displayName || recipientResolution.email;
        }
        if (recipientOrgGroup?.name)
        {
            return `Group: ${recipientOrgGroup.name}`;
        }
        if (recipientOrgUser?.person?.firstName || recipientOrgUser?.person?.lastName)
        {
            return `${recipientOrgUser.person?.firstName ?? ''} ${recipientOrgUser.person?.lastName ?? ''}`.trim();
        }
        if (recipientOrgUser?.email)
        {
            return recipientOrgUser.email;
        }
        if (newRecipient?.firstName || newRecipient?.lastName)
        {
            return `${newRecipient.firstName ?? ''} ${newRecipient.lastName ?? ''}`.trim();
        }
        return newRecipient?.email || 'Recipient';
    };

    const copyTextWithFallback = async (text: string): Promise<boolean> =>
    {
        try
        {
            if (navigator.clipboard?.writeText)
            {
                await navigator.clipboard.writeText(text);
                return true;
            }
        }
        catch
        {
            // Fallback below for environments where clipboard APIs are blocked.
        }

        const textArea = document.createElement('textarea');
        textArea.value = text;
        textArea.style.position = 'fixed';
        textArea.style.opacity = '0';
        document.body.appendChild(textArea);
        textArea.focus();
        textArea.select();
        const success = document.execCommand('copy');
        document.body.removeChild(textArea);
        return success;
    };

    const onCopyExchangeLink = async () =>
    {
        const link = createdExchangeSummary?.shareLink;
        if (!link)
        {
            setCopyLinkStatus('failed');
            return;
        }

        const copied = await copyTextWithFallback(link);
        setCopyLinkStatus(copied ? 'copied' : 'failed');
    };

    const onViewExchange = () =>
    {
        if (!createdExchangeSummary?.id)
        {
            return;
        }
        resetInitiationForm();
        setIsDialogOpen(false);
        navigate(`/exchanges?tab=inbox&role=outgoing&s=${encodeURIComponent(createdExchangeSummary.id)}`);
    };

    const onManageInformationRequest = (requestId: string) => {
        resetInitiationForm();
        setIsDialogOpen(false);
        navigate(informationRequestManagePath(requestId));
    };

    const buildExchangeShareLink = (exchangeId: string, requiresSignIn: boolean): string =>
    {
        const encodedExchangeId = encodeURIComponent(exchangeId);
        const route = requiresSignIn ? '/exchanges' : '/nas';
        return `${window.location.origin}${route}?s=${encodedExchangeId}`;
    };

    const isRecipientValid = (): boolean =>
    {
        const validateEmailRecipient = (): boolean =>
        {
            if (!newRecipient || !newRecipient.email)
            {
                setMessageGroupMessages(['A valid recipient email is required']);
                setSelectedTab('recipients-tab');
                return false;
            }
            if (!newRecipient.firstName || !newRecipient.lastName)
            {
                setMessageGroupMessages(['Recipient first and last name are required']);
                setSelectedTab('recipients-tab');
                return false;
            }
            if (appUser?.email && newRecipient.email.trim().toLowerCase() === appUser.email.trim().toLowerCase())
            {
                setMessageGroupMessages(['You cannot be the recipient of your own Exchange']);
                setSelectedTab('recipients-tab');
                return false;
            }
            return true;
        };

        switch (recipientMode)
        {
            case ExchangeInitiationRecipientMode.TRUSTED_ORG:
                if (!recipientOrg)
                {
                    setMessageGroupMessages(['A valid recipient organization is required']);
                    setSelectedTab('recipients-tab');
                    return false;
                }
                if (recipientResolution && recipientResolution.expiresAt <= Date.now())
                {
                    setMessageGroupMessages(['The trusted member verification expired. Verify the member again.']);
                    setSelectedTab('recipients-tab');
                    return false;
                }
                if (!recipientResolution && !recipientOrgGroup)
                {
                    setMessageGroupMessages(['Verify a member or select a published group from the Trusted Organization']);
                    setSelectedTab('recipients-tab');
                    return false;
                }
                break;
            case ExchangeInitiationRecipientMode.MY_ORG:
                if (!recipientOrgUser && !recipientOrgGroup)
                {
                    setMessageGroupMessages(['A valid recipient user or group in your organization is required']);
                    setSelectedTab('recipients-tab');
                    return false;
                }
                if (recipientOrgUser && appUser && recipientOrgUser.id === appUser.id)
                {
                    setMessageGroupMessages(['You cannot be the recipient of your own Exchange']);
                    setSelectedTab('recipients-tab');
                    return false;
                }
                break;
            case ExchangeInitiationRecipientMode.MY_GROUPS:
                if (!recipientOrgGroup)
                {
                    setMessageGroupMessages(['Please select one of your personal groups as the recipient']);
                    setSelectedTab('recipients-tab');
                    return false;
                }
                break;
            case ExchangeInitiationRecipientMode.PEOPLE:
                if (recipientOrgUser)
                {
                    if (appUser && recipientOrgUser.id === appUser.id)
                    {
                        setMessageGroupMessages(['You cannot be the recipient of your own Exchange']);
                        setSelectedTab('recipients-tab');
                        return false;
                    }
                    break;
                }
                if (!validateEmailRecipient()) return false;
                break;
            case ExchangeInitiationRecipientMode.EMAIL:
                if (!validateEmailRecipient()) return false;
                break;
        }
        return true;
    };

    const onInitiateExchange = async () =>
    {

        if (initiatingExchange)
        {
            return;
        }

        setMessageGroupMessages([]);
        setInitiatingExchange(true);

        try
        {
            if (!isRecipientValid())
            {
                return;
            }

            if (!name)
            {
                setMessageGroupMessages(['Exchange name is required']);
                setSelectedTab('details-tab');
                return;
            }

            const documentProblem = exchangeInitiationDocumentProblem(documents, startsInformationRequest);
            if (documentProblem)
            {
                setMessageGroupMessages([documentProblem]);
                setSelectedTab('documents-tab');
                return;
            }

            const primaryRecipient = buildRecipientSelection({
                mode: recipientMode,
                organization: recipientOrg,
                appUser: recipientOrgUser,
                group: recipientOrgGroup,
                externalRecipient: newRecipient,
                resolutionId: recipientResolution?.id,
            });
            const exchange: ExchangeInitiationRequest = {
                name,
                description,
                primaryRecipient,
                initialShareMessage,
                exchangeDocuments: documents.map((doc: ExchangeRequestDocumentRequest) => ({
                    ...doc,
                    restrictedType: doc.restrictType ? doc.restrictedType : undefined
                })),
                requestRecipientSignIn: requireSignIn,
                allowDocumentAddition: allowDocumentAdditions,
                allowDocumentDeletion: allowDocumentDeletions,
                allowDocumentDownload: allowDocumentDownload,
                allowDocumentUpdate: allowDocumentUpdate,
                allowDocumentUpload: allowDocumentUpload,
                allowedDownloadFormats: allowDocumentDownload ? (allowedDownloadFormats ?? undefined) : undefined,
                recipientRoleName: recipientRole,
                recipientConstraintsJson:
                    Object.keys(recipientConstraints).length > 0
                        ? JSON.stringify(recipientConstraints)
                        : undefined,
                participants: internalParticipants
                    ?.filter(p => !appUser || p.id !== appUser.id)
                    ?.map((participant) => ({
                        selection: {type: "REGISTERED_USER", appUserId: participant.id},
                        role: ExchangeShareRoleName.PARTICIPANT,
                    })),
                variableOverrides: Object.keys(variableOverrides).length > 0 ? variableOverrides : undefined,
                schemaDefinitionId: schemaDefinitionId || undefined,
                fieldValues: buildCreationFieldValues(schemaDefinitionId, fieldBindings, fieldValueMap),
                schemaAssignmentSource: schemaFromBlueprint && schemaDefinitionId
                    ? SchemaAssignmentSource.BLUEPRINT
                    : undefined,
                blueprintDefinitionId: selectedBlueprint?.id,
            };

            const createdExchange = await initiateExchange(exchange);
            const createdExchangeId = (createdExchange as { id?: string })?.id;
            const shareLink = createdExchangeId ? buildExchangeShareLink(createdExchangeId, requireSignIn) : undefined;
            const informationRequest = startsInformationRequest && createdExchangeId
                ? await findStartedInformationRequest(createdExchangeId)
                : undefined;

            setCreatedExchangeSummary({
                id: createdExchangeId,
                name: name.trim(),
                recipient: buildRecipientLabel(),
                documents: documents.length,
                requiresSignIn: requireSignIn,
                shareLink,
                informationRequest,
            });
            setCopyLinkStatus('idle');

            publishNewExchangeAddition(createdExchange);

            setExchangeInitiatedSuccessfully(true);
            await refreshCurrentSession();
        }
        catch (error: unknown)
        {
            showServerErrorToast(exchangeInitiationRefusalMessage(error));
        }
        finally
        {
            setInitiatingExchange(false);
        }
    };

    const addNewDocument = () =>
    {
        setMessageGroupMessages([]);
        setDocuments([...documents, {
            title: '',
            restrictedType: DocumentType.PDF,
            restrictType: false
        }]);
    };

    const addLibraryDocument = (entry: DocumentLibraryEntrySummaryDto) =>
    {
        setMessageGroupMessages([]);
        setDocuments(prev => [...prev, {
            title: entry.title,
            libraryDocumentId: entry.id,
            restrictType: entry.restrictType ?? false,
            restrictedType: entry.restrictedType,
            required: entry.required ?? false,
        } as ExchangeRequestDocumentRequest]);
    };

    const resetInitiationForm = () =>
    {
        setChoosingBlueprint(false);
        clearAppliedBlueprint();
        setMessageGroupMessages([]);
        setInitiatingExchange(false);
        setExchangeInitiatedSuccessfully(false);
        setRecipientOrg(null)
        setRecipientOrgUser(null)
        setRecipientOrgGroup(null)
        setRecipientMode(ExchangeInitiationRecipientMode.PEOPLE);
        setNewRecipient({
            email: '',
            firstName: '',
            lastName: '',
        });
        setExchangeName('');
        setDescription('');
        setInitialShareMessage('');
        setRequireSignIn(true);
        setAllowDocumentAdditions(false);
        setAllowDocumentDeletions(false);
        setAllowDocumentDownload(false);
        setAllowDocumentUpdate(false);
        setAllowDocumentUpload(false);
        setAllowedDownloadFormats(undefined);
        setRequestingDocuments(true);
        setDocuments([]);
        setSelectedTab('recipients-tab');
        setCreatedExchangeSummary(null);
        setCopyLinkStatus('idle');
        setRecipientRole(undefined);
        setRecipientConstraints({});
    };

    const onCancelInitiation = () =>
    {
        resetInitiationForm();
        setIsDialogOpen(false);
    };

    const onDialogOpenChange = (_: unknown, data: { open: boolean }) =>
    {
        if (data.open)
        {
            setExchangeInitiatedSuccessfully(false);
            setCreatedExchangeSummary(null);
            setCopyLinkStatus('idle');
            setMessageGroupMessages([]);
            getAvailableVariables().then(setAvailableVariables).catch(() => null);
            listSchemas()
                .then(all => setEligibleSchemas(filterEligibleExchangeSchemas(all)))
                .catch(() => setEligibleSchemas([]));
        }
        else
        {
            resetInitiationForm();
        }
        setIsDialogOpen(data.open);
    };

    React.useEffect(() =>
    {
        const subscription = recreateRejectedExchangeObservable.subscribe(draft =>
        {
            clearAppliedBlueprint();
            setChoosingBlueprint(false);
            setMessageGroupMessages([]);
            setExchangeInitiatedSuccessfully(false);
            setInitiatingExchange(false);

            setExchangeName(draft.name || '');
            setDescription(draft.description || '');
            setInitialShareMessage(draft.initialShareMessage || '');
            setRequireSignIn(!!draft.requestRecipientSignIn);
            setAllowDocumentAdditions(!!draft.allowDocumentAddition);
            setAllowDocumentDeletions(!!draft.allowDocumentDeletion);
            setAllowDocumentDownload(!!draft.allowDocumentDownload);
            setAllowDocumentUpdate(!!draft.allowDocumentUpdate);
            setAllowDocumentUpload(!!draft.allowDocumentUpload);
            setAllowedDownloadFormats(draft.allowedDownloadFormats);
            setDocuments(draft.exchangeDocuments || []);
            setSelectedTab('details-tab');
            setRecipientOrg(undefined);
            setRecipientOrgGroup(undefined);

            if (draft.recipientUser)
            {
                setRecipientMode(ExchangeInitiationRecipientMode.PEOPLE);
                setRecipientOrgUser(draft.recipientUser);
                setNewRecipient({
                    email: '',
                    firstName: '',
                    lastName: '',
                });
            }
            else
            {
                setRecipientMode(ExchangeInitiationRecipientMode.PEOPLE);
                setRecipientOrgUser(undefined);
                setNewRecipient({
                    email: draft.recipientEmail || '',
                    firstName: draft.recipientFirstName || '',
                    lastName: draft.recipientLastName || '',
                });
            }

            setIsDialogOpen(true);
        });

        return () => subscription.unsubscribe();
    }, [
        setAllowDocumentAdditions,
        setAllowDocumentDeletions,
        setAllowDocumentDownload,
        setAllowDocumentUpdate,
        setAllowDocumentUpload,
        setAllowedDownloadFormats,
        clearAppliedBlueprint,
        setChoosingBlueprint,
        setDescription,
        setDocuments,
        setExchangeInitiatedSuccessfully,
        setExchangeName,
        setInitialShareMessage,
        setInitiatingExchange,
        setMessageGroupMessages,
        setNewRecipient,
        setRecipientMode,
        setRecipientOrg,
        setRecipientOrgGroup,
        setRecipientOrgUser,
        setRequireSignIn,
        setSelectedTab,
    ]);

    const renderRecipientsTab = () =>
    {
        return (
            <ExchangeInitiationRecipientsTab
                recipientMode={recipientMode}
                setRecipientMode={setRecipientMode}
                recipientOrg={recipientOrg}
                setRecipientOrg={setRecipientOrg}
                recipientOrgUser={recipientOrgUser}
                setRecipientOrgUser={setRecipientOrgUser}
                recipientOrgGroup={recipientOrgGroup}
                setRecipientOrgGroup={setRecipientOrgGroup}
                setRecipientResolution={setRecipientResolution}
                internalParticipants={internalParticipants}
                setInternalParticipants={setInternalParticipants}
                newRecipient={newRecipient}
                setNewRecipient={setNewRecipient}
                isRequestingDocuments={requestingDocuments}
                recipientRole={recipientRole}
                setRecipientRole={setRecipientRole}
                recipientConstraints={recipientConstraints}
                setRecipientConstraints={setRecipientConstraints}
                allowAdditionalParticipants={participantAvailability.isAvailable}
                allowAdvancedAccessControls={advancedAccessAvailability.isAvailable}
            />
        )
    }

    const renderDetailsTab = () =>
    {
        return (
            <ExchangeInitiationDetailsTab
                name={name}
                description={description}
                initialShareMessage={initialShareMessage}
                onExchangeNameChange={handleInputChange(setExchangeName)}
                onDescriptionChange={handleInputChange(setDescription)}
                onInitialShareMessageChange={handleInputChange(setInitialShareMessage)}
                setMessageGroupMessages={setMessageGroupMessages}
                availableVariables={availableVariables ?? undefined}
                onNameChange={setExchangeName}
                onDescChange={setDescription}
                onMessageChange={setInitialShareMessage}
                locked={blueprintLocked}
            />
        )
    }

    const renderDocumentsTab = () =>
    {
        return (
            <SharingDocumentsTab
                documents={documents}
                onDocumentNameChange={(index, value) =>
                {
                    setMessageGroupMessages([]);
                    handleDocumentChange(documents, setDocuments)(index, 'title', value);
                }}
                onDocumentTypeChange={(index, value) =>
                {
                    setMessageGroupMessages([]);
                    handleDocumentChange(documents, setDocuments)(index, 'restrictedType', value);
                }}
                onRestrictDocumentTypeChange={(index, ev) =>
                {
                    setMessageGroupMessages([]);
                    handleDocumentChange(documents, setDocuments)(index, 'restrictType', ev.target.checked);
                }}
                onRequiredChange={(index, value) =>
                {
                    setMessageGroupMessages([]);
                    handleDocumentChange(documents, setDocuments)(index, 'required', value);
                }}
                onDeleteDocument={(index) =>
                {
                    setMessageGroupMessages([]);
                    setDocuments(prevDocuments =>
                    {
                        const updatedDocuments = prevDocuments.filter((_, i) => i !== index);
                        return updatedDocuments;
                    });
                }}
                onUnlink={(index) =>
                {
                    setMessageGroupMessages([]);
                    handleDocumentChange(documents, setDocuments)(index, 'libraryDocumentId', undefined);
                }}
                addNewDocument={addNewDocument}
                addLibraryDocument={addLibraryDocument}
                availableVariables={availableVariables ?? undefined}
                locked={blueprintLocked}
                canUseDocumentLibrary={documentLibraryAvailability.isAvailable}
                informationRequestCollectsDocuments={startsInformationRequest}
            />
        )
    }

    const renderOptionsTab = () =>
    {
        return (
            <SharingOptionsTab
                requireSignIn={requireSignIn}
                allowDocumentAdditions={allowDocumentAdditions}
                allowDocumentDeletions={allowDocumentDeletions}
                allowDocumentDownload={allowDocumentDownload}
                allowDocumentUpdate={allowDocumentUpdate}
                allowDocumentUpload={allowDocumentUpload}
                onRequireSignInChange={handleCheckboxChange(setRequireSignIn)}
                onAllowDocumentAdditionsChange={handleCheckboxChange(setAllowDocumentAdditions)}
                onAllowDocumentDeletionsChange={handleCheckboxChange(setAllowDocumentDeletions)}
                onAllowDocumentDownloadChange={handleCheckboxChange(setAllowDocumentDownload)}
                onAllowDocumentUpdateChange={handleCheckboxChange(setAllowDocumentUpdate)}
                onAllowDocumentUploadChange={handleCheckboxChange(setAllowDocumentUpload)}
                allowedDownloadFormats={allowedDownloadFormats}
                onAllowedDownloadFormatsChange={setAllowedDownloadFormats}
                locked={blueprintLocked}
            />
        )
    }

    const handleSchemaChange = (id: string | undefined) =>
    {
        setSchemaFromBlueprint(false);
        setSchemaDefinitionId(id || undefined);
        setFieldValueMap({});
        setFieldBindings([]);
    };

    const renderFieldsTab = () =>
    {
        return (
            <ExchangeInitiationFieldsTab
                schemas={eligibleSchemas}
                schemaDefinitionId={schemaDefinitionId}
                onSchemaChange={handleSchemaChange}
                bindings={fieldBindings}
                onBindingsLoaded={setFieldBindings}
                valueMap={fieldValueMap}
                onValueChange={(fieldContractId, value) =>
                    setFieldValueMap(prev => ({...prev, [fieldContractId]: value}))}
                locked={blueprintLocked}
            />
        )
    }

    const renderVariableOverridesPanel = () =>
    {
        if (pendingVariableTokens.length === 0) return null;
        return (
            <div id={"exchange-initiation-variable-overrides-panel"}
                 className={styles.variableOverridesPanel}>
                <Text id={"exchange-initiation-fill-in-variables-text"}
                      weight="semibold"
                      size={300}>Fill in variables</Text>
                <Text id={"exchange-initiation-variable-overrides-subtext"}
                      size={200}
                      className={styles.variableOverridesSubtext}>
                    Override the default values for this exchange.
                </Text>
                {pendingVariableTokens.map(token => (
                    <div id={`exchange-initiation-variable-overrides-row-${token}`}
                         key={token}
                         className={styles.variableOverridesRow}>
                        <Text id={`exchange-initiation-variable-overrides-label-${token}`}
                              size={200}
                              className={styles.variableOverridesLabel}>{`{{${token}}}`}</Text>
                        <input
                            id={`exchange-initiation-variable-overrides-input-${token}`}
                            className={styles.variableOverridesInput}
                            value={variableOverrides[token] ?? ''}
                            onChange={e => setVariableOverrides(prev => ({...prev, [token]: e.target.value}))}
                            placeholder={`Value for ${token}`}
                        />
                    </div>
                ))}
            </div>
        );
    };

    const renderTabs = () =>
    {
        return (
            <div id={"exchange-initiation-taps"}
                 className={styles.exchangeInitiationTaps}>
                {pendingVariableTokens.length > 0 && renderVariableOverridesPanel()}
                {selectedTab === "recipients-tab" && renderRecipientsTab()}
                {selectedTab === "details-tab" && renderDetailsTab()}
                {selectedTab === "fields-tab" && renderFieldsTab()}
                {selectedTab === "documents-tab" && renderDocumentsTab()}
                {selectedTab === "options-tab" && renderOptionsTab()}
            </div>
        )
    }

    const renderErrorMessageBar = () =>
    {
        const onCloseMessageBar = (index: number) =>
        {
            setMessageGroupMessages(messageGroupMessages.filter((_, i) => i !== index));
        }

        return <>
            {messageGroupMessages &&
                <MessageBarGroup id={"exchange-initiation-error-messages-group"}
                                 className={styles.errorMessagesGroup}>
                    {messageGroupMessages.map((message: string, index: number) => (
                        <MessageBar id={`exchange-initiation-message-bar-${index}`}
                                    key={index}
                                    intent={"warning"}>
                            <MessageBarBody id={`exchange-initiation-message-bar-body-${index}`}>
                                {message}
                            </MessageBarBody>
                            <MessageBarActions
                                id={`exchange-initiation-message-bar-actions-${index}`}
                                containerAction={
                                    <Button
                                        id={`exchange-close-message-bar-${index}`}
                                        onClick={() => onCloseMessageBar(index)}
                                        appearance="transparent"
                                        shape={"circular"}
                                        icon={<DismissRegular/>}/>
                                }
                            />
                        </MessageBar>
                    ))}
                </MessageBarGroup>
            }
        </>
    }

    const renderDialogContent = () =>
    {
        return <>
            {exchangeInitiatedSuccessfully ? (
                <div id={"exchange-initiation-success"}
                     className={styles.exchangeInitiationSuccess}>
                    <Text id={"exchange-initiation-exchange-started-successfully-text"}
                          size={500}> Exchange started successfully </Text>
                    <Text id={"exchange-initiation-name-text"}
                          size={300}
                          italic={true}> {createdExchangeSummary?.name || name} </Text>
                    <div id={"exchange-initiation-exchange-success-details"}
                         className={styles.exchangeSuccessDetails}>
                        <Text id={"exchange-initiation-recipient-text"}
                              size={200}>Recipient</Text>
                        <Text id={"exchange-initiation-recipient-text-2"}
                              size={200}>{createdExchangeSummary?.recipient || '-'}</Text>
                        <Text id={"exchange-initiation-documents-text"}
                              size={200}>Documents</Text>
                        <Text id={"exchange-initiation-length-text"}
                              size={200}>{createdExchangeSummary?.documents ?? documents.length}</Text>
                        <Text id={"exchange-initiation-recipient-sign-in-text"}
                              size={200}>Recipient sign-in</Text>
                        <Text
                            id={"exchange-initiation-required-text"}
                            size={200}>{(createdExchangeSummary?.requiresSignIn ?? requireSignIn) ? 'Required' : 'Not required'}</Text>
                    </div>
                    {createdExchangeSummary?.informationRequest !== undefined && (
                        <CreatedInformationRequestSummary request={createdExchangeSummary.informationRequest}
                                                          onManage={onManageInformationRequest}/>
                    )}
                    <div id={"exchange-initiation-exchange-success-actions"}
                         className={styles.exchangeSuccessActions}>
                        <Button
                            id={"exchange-copy-link-btn"}
                            appearance={"subtle"}
                            shape={"circular"}
                            onClick={onCopyExchangeLink}
                            disabled={!createdExchangeSummary?.shareLink}
                        >
                            Copy Link
                        </Button>
                        <Button
                            id={"exchange-view-exchange-btn"}
                            shape={"circular"}
                            appearance={"subtle"}
                            onClick={onViewExchange}
                            disabled={!createdExchangeSummary?.id}
                        >
                            View Exchange
                        </Button>
                    </div>
                    {copyLinkStatus === 'copied' && <Text id={"exchange-initiation-link-copied-text"}
                                                          size={200}>Link copied</Text>}
                    {copyLinkStatus === 'failed' && <Text id={"exchange-initiation-could-not-copy-link-text"}
                                                          size={200}>Could not copy link</Text>}
                </div>
            ) : (
                <div id={"exchange-initiation-dialog-content-container"}
                     className={styles.dialogContentContainer}>
                    {choosingBlueprint ? (
                        <BlueprintPicker
                            onSelect={handleBlueprintSelect}
                            onCancel={closeBlueprintPicker}
                        />
                    ) : renderTabs()}
                </div>
            )}
        </>
    }

    const dialogContentKey = saveBlueprintDialogOpen
        ? "save-blueprint"
        : exchangeInitiatedSuccessfully
            ? "success"
            : choosingBlueprint
                ? "blueprint-picker"
                : "initiation-form";

    const dialogContentTransitionClassName = mergeClasses(
        styles.dialogContentTransitionFrame,
        contentTransitionDirection === "forward" ? styles.dialogContentSlideInFromRight : undefined,
        contentTransitionDirection === "back" ? styles.dialogContentSlideInFromLeft : undefined,
    );

    return (
        <Dialog modalType="alert"
                open={isDialogOpen}
                onOpenChange={onDialogOpenChange}
        >
            <DialogTrigger disableButtonEnhancement>
                <ExchangeInitiationDialogTrigger
                    onRequestingDocumentsChange={handleRequestingDocumentsChange}
                    onChooseBlueprint={openBlueprintPicker}
                    canUseBlueprints={blueprintAvailability.isAvailable}
                />
            </DialogTrigger>
            <DialogSurface id={"exchange-initiation-dialog"}
                           className={styles.dialog}>
                <DialogBody id={"exchange-initiation-dialog-actions-dialog-body"}>
                    <DialogTitle id={"exchange-initiation-dialog-title"}
                                 className={styles.dialogTitle}>
                        {saveBlueprintDialogOpen ? (
                            <div id={"exchange-initiation-save-blueprint-back-row"}
                                 className={styles.saveBlueprintBackRow}>
                                <Button
                                    id={"exchange-save-blueprint-back-btn"}
                                    appearance="subtle"
                                    shape="circular"
                                    size="small"
                                    icon={<ArrowLeftRegular/>}
                                    onClick={closeSaveBlueprintPanel}
                                    aria-label="Back to Exchange"
                                />
                                <Text id={"exchange-initiation-save-as-blueprint-text"}
                                      weight="semibold"
                                      size={500}>Save as Blueprint</Text>
                            </div>
                        ) : (
                            <>
                                <ExchangeInitiationDialogTitleSection
                                    exchangeInitiatedSuccessfully={exchangeInitiatedSuccessfully}
                                    requestingDocuments={requestingDocuments}
                                    choosingBlueprint={choosingBlueprint}
                                    selectedBlueprintName={selectedBlueprint?.name ?? null}
                                    selectedTab={selectedTab}
                                    showFieldsTab={businessFieldsAvailability.isAvailable && eligibleSchemas.length > 0}
                                    onTabSelect={(value) =>
                                    {
                                        setMessageGroupMessages([]);
                                        setSelectedTab(value);
                                    }}
                                    onSaveAsBlueprint={blueprintManagementAvailability.isAvailable
                                        ? openSaveBlueprintPanel
                                        : undefined}
                                />
                                {renderErrorMessageBar()}
                            </>
                        )}
                    </DialogTitle>
                    <DialogContent id={"exchange-initiation-dialog-content"}
                                   className={styles.dialogContent}>
                        <div
                            id={`exchange-initiation-content-transition-${dialogContentKey}`}
                            key={dialogContentKey}
                            className={dialogContentTransitionClassName}
                        >
                            {saveBlueprintDialogOpen ? (
                                <SaveBlueprintPanel
                                    onSaved={closeSaveBlueprintPanel}
                                    initialName={name}
                                    configJson={buildBlueprintConfigJson()}
                                    exchangeDocuments={buildBlueprintDocuments()}
                                    participants={blueprintParticipantsOf(internalParticipants ?? [])}
                                    schemaDefinitionId={schemaDefinitionId || undefined}
                                    fieldDefaults={buildBlueprintFieldDefaults(schemaDefinitionId, fieldBindings, fieldValueMap)}
                                />
                            ) : renderDialogContent()}
                        </div>
                    </DialogContent>
                    {!saveBlueprintDialogOpen && (
                        <DialogActions id={"exchange-initiation-dialog-actions"}>
                            <ExchangeInitiationDialogActions
                                requestingDocuments={requestingDocuments}
                                initiatingExchange={initiatingExchange}
                                exchangeInitiatedSuccessfully={exchangeInitiatedSuccessfully}
                                choosingBlueprint={choosingBlueprint}
                                onResetInitiation={resetInitiationForm}
                                onCloseDialog={onCancelInitiation}
                                onInitiateExchange={() => onInitiateExchange()}
                            />
                        </DialogActions>
                    )}
                    <Toaster id={"exchange-initiation-dialog-toaster"}
                             inline
                             toasterId={toasterId}
                             position="bottom"/>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default ExchangeInitiation;
