import React from 'react';
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger, Link,
    mergeClasses,
    MessageBar,
    MessageBarActions,
    MessageBarBody,
    MessageBarGroup,
    Text,
    Toast,
    Toaster,
    ToastTitle, ToastTrigger,
    useId,
    useToastController,
} from "@fluentui/react-components";
import {initiateExchange} from "../../services/exchangeApi.ts";
import useExchangeInitiatingState from './hooks/useExchangeInitiatingState.ts';
import {handleCheckboxChange, handleDocumentChange, handleInputChange} from './formHandlers.tsx';
import SharingDocumentsTab from "./components/exchange-initiation-documents-tab/ExchangeInitiationDocumentsTab.tsx";
import ExchangeInitiationDetailsTab from "./components/exchange-initiation-details-tab/ExchangeInitiationDetailsTab.tsx";
import SharingOptionsTab from "./components/exchange-initiation-options-tab/ExchangeInitiationOptionsTab.tsx";
import ExchangeInitiationDialogActions
    from "./components/exchange-initiation-dialog-actions/ExchangeInitiationDialogActions.tsx";
import ExchangeInitiationDialogTrigger
    from "./components/exchange-initiation-dialog-trigger/ExchangeInitiationDialogTrigger.tsx";
import ExchangeInitiationDialogTitleSection
    from "./components/exchange-initiation-dialog-title-section/ExchangeInitiationDialogTitleSection.tsx";
import ExchangeInitiationFieldsTab
    from "./components/exchange-initiation-fields-tab/ExchangeInitiationFieldsTab.tsx";
import {ArrowLeftRegular, DismissRegular} from "@fluentui/react-icons";
import ExchangeInitiationRecipientsTab
    from "./components/exchange-initiation-recipients-tab/ExchangeInitiationRecipientsTab.tsx";
import {
    ExchangeInitiationRecipientMode
} from "./components/exchange-initiation-recipients-tab/exchangeInitiationRecipientMode.ts";
import {publishNewExchangeAddition} from '../observable/exchangeObservables.ts';
import {useExchangeInitiationStyles} from "./ExchangeInitiationStyles.tsx";
import {
    AvailableVariablesDto,
    BlueprintConfig,
    BlueprintDefinitionSummaryDto,
    BlueprintDocumentConfig,
    DocumentType,
    DocumentLibraryEntrySummaryDto,
    ExchangeInitiationRequest,
    ExchangeRequestDocumentRequest,
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
import {recreateRejectedExchangeObservable} from "../observable/exchangeObservables.ts";
import {useNavigate} from "react-router-dom";
import {buildRecipientSelection} from "./exchangeInitiationRecipientSelection.ts";
import {ExchangeShareRoleName} from "../../services/types/roles.ts";

type CreatedExchangeSummary = {
    id?: string;
    name: string;
    recipient: string;
    documents: number;
    requiresSignIn: boolean;
    shareLink?: string;
};
type ExchangeInitiationTransitionDirection = "forward" | "back" | null;

const ExchangeInitiation: React.FC = () =>
{
    const styles = useExchangeInitiationStyles();
    const {appUser} = useAuth();
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
    const [selectedBlueprintName, setSelectedBlueprintName] = React.useState<string | null>(null);
    const [blueprintLocked, setBlueprintLocked] = React.useState(false);
    const [eligibleSchemas, setEligibleSchemas] = React.useState<SchemaDefinitionDto[]>([]);
    const [schemaFromBlueprint, setSchemaFromBlueprint] = React.useState(false);
    const [createdExchangeSummary, setCreatedExchangeSummary] = React.useState<CreatedExchangeSummary | null>(null);
    const [copyLinkStatus, setCopyLinkStatus] = React.useState<'idle' | 'copied' | 'failed'>('idle');
    const [saveBlueprintDialogOpen, setSaveBlueprintDialogOpen] = React.useState(false);
    const [availableVariables, setAvailableVariables] = React.useState<AvailableVariablesDto | null>(null);
    const [variableOverrides, setVariableOverrides] = React.useState<Record<string, string>>({});
    const [pendingVariableTokens, setPendingVariableTokens] = React.useState<string[]>([]);
    const [contentTransitionDirection, setContentTransitionDirection] =
        React.useState<ExchangeInitiationTransitionDirection>(null);

    const {dispatchToast} = useToastController(toasterId);

    const openBlueprintPicker = () =>
    {
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
            <Toast>
                <ToastTitle action={
                    <ToastTrigger>
                        <Link>Dismiss</Link>
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
            // Request mode: recipient uploads the requested docs; they don't need download access
            setAllowDocumentUpload(true);
            setAllowDocumentAdditions(true);
            setAllowDocumentDownload(false);
            setAllowDocumentUpdate(false);
            setAllowDocumentDeletions(false);
        }
        else
        {
            // Send mode: recipient downloads the sent docs; they shouldn't modify the exchange
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

    /**
     * Seeds the wizard's schema + field values from a selected blueprint. Defaults are keyed by the
     * stable fieldDefinitionId; they are mapped to the current published version's fieldContractId via
     * the resolved schema, so any field dropped from the schema is silently skipped (never blocks the
     * start). Marks the assignment source BLUEPRINT so the backend records provenance.
     */
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
                setFieldBindings([]);
                setFieldValueMap({});
            });
    };

    const handleBlueprintSelect = (blueprint: BlueprintDefinitionSummaryDto) =>
    {
        try
        {
            const config: BlueprintConfig = JSON.parse(blueprint.configJson);
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
            if (blueprint.participants && blueprint.participants.length > 0)
            {
                setInternalParticipants(blueprint.participants);
            }
            setBlueprintLocked(blueprint.scope !== 'PERSONAL' && !(config.allowEditOnExchangeStart === true));
            applyBlueprintSchema(blueprint);
        }
        catch
        {
            // Invalid configJson; apply what we can, ignore the rest
        }
        setSelectedBlueprintName(blueprint.name);
        setContentTransitionDirection("forward");
        setChoosingBlueprint(false);
        setSelectedTab('recipients-tab');

        // Scan applied strings for non-system, non-SEQ tokens (org/personal vars needing override)
        if (availableVariables)
        {
            const systemTokenSet = new Set(availableVariables.system.map(s => s.token));
            const seqTokenSet = new Set(availableVariables.sequences.map(s => `SEQ:${s.key}`));
            const allStrings = [
                config.name ?? '',
                config.description ?? '',
                config.initialShareMessage ?? '',
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
        setIsDialogOpen(false);
        navigate(`/exchanges?s=${encodeURIComponent(createdExchangeSummary.id)}`);
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
                setMessageGroupMessages(['You cannot be the recipient of your own exchange']);
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
                    setMessageGroupMessages(['You cannot be the recipient of your own exchange']);
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
                // PEOPLE produces either a selected real user (recipientOrgUser) or an
                // email-based new recipient (newRecipient). Validate whichever was set.
                if (recipientOrgUser)
                {
                    if (appUser && recipientOrgUser.id === appUser.id)
                    {
                        setMessageGroupMessages(['You cannot be the recipient of your own exchange']);
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

            if (documents.length === 0)
            {
                setMessageGroupMessages(['At least one document is required when requesting documents']);
                setSelectedTab('documents-tab');
                return;
            }

            if (documents.some(doc => !doc.title))
            {
                setMessageGroupMessages(['All documents must have names']);
                setSelectedTab('documents-tab');
                return;
            }

            if (documents.some(doc => doc.restrictType && !doc.restrictedType))
            {
                setMessageGroupMessages(['All restricted documents must have a type']);
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
            };

            const createdExchange = await initiateExchange(exchange);
            const createdExchangeId = (createdExchange as { id?: string })?.id;
            const shareLink = createdExchangeId ? buildExchangeShareLink(createdExchangeId, requireSignIn) : undefined;

            setCreatedExchangeSummary({
                id: createdExchangeId,
                name: name.trim(),
                recipient: buildRecipientLabel(),
                documents: documents.length,
                requiresSignIn: requireSignIn,
                shareLink,
            });
            setCopyLinkStatus('idle');

            publishNewExchangeAddition(createdExchange);

            setExchangeInitiatedSuccessfully(true);
        }
        catch (error)
        {
            let errorMessage = error.response?.data?.errorMessage || error.message;

            if (!errorMessage)
            {
                errorMessage = "An error unknown occurred while initiating exchange";
            }

            showServerErrorToast(errorMessage);

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
        setSelectedBlueprintName(null);
        setBlueprintLocked(false);
        setMessageGroupMessages([]);
        setInitiatingExchange(false);
        setExchangeInitiatedSuccessfully(false);
        setRecipientOrg(null)
        setRecipientOrgUser(null)
        setRecipientOrgGroup(null)
        setRecipientMode(ExchangeInitiationRecipientMode.PEOPLE);
        setInternalParticipants(undefined);
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
        setSchemaDefinitionId(undefined);
        setFieldValueMap({});
        setFieldBindings([]);
        setSchemaFromBlueprint(false);
        setVariableOverrides({});
        setPendingVariableTokens([]);
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
            // Reset only the result/success state so stale details are never shown when
            // reopening. The trigger callbacks (onRequestingDocumentsChange, onChooseBlueprint)
            // run in the same batched event and set their own state independently, so we must
            // not touch choosingBlueprint or requestingDocuments here.
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
            <div className={styles.variableOverridesPanel}>
                <Text weight="semibold" size={300}>Fill in variables</Text>
                <Text size={200} className={styles.variableOverridesSubtext}>
                    Override the default values for this exchange.
                </Text>
                {pendingVariableTokens.map(token => (
                    <div key={token} className={styles.variableOverridesRow}>
                        <Text size={200} className={styles.variableOverridesLabel}>{`{{${token}}}`}</Text>
                        <input
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
            <div className={styles.exchangeInitiationTaps}>
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
                <MessageBarGroup className={styles.errorMessagesGroup}>
                    {messageGroupMessages.map((message: string, index: number) => (
                        <MessageBar key={index} intent={"warning"}>
                            <MessageBarBody>
                                {message}
                            </MessageBarBody>
                            <MessageBarActions
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
                <div className={styles.exchangeInitiationSuccess}>
                    <Text size={500}> Exchange started successfully </Text>
                    <Text size={300} italic={true}> {createdExchangeSummary?.name || name} </Text>
                    <div className={styles.exchangeSuccessDetails}>
                        <Text size={200}>Recipient</Text>
                        <Text size={200}>{createdExchangeSummary?.recipient || '-'}</Text>
                        <Text size={200}>Documents</Text>
                        <Text size={200}>{createdExchangeSummary?.documents ?? documents.length}</Text>
                        <Text size={200}>Recipient sign-in</Text>
                        <Text size={200}>{(createdExchangeSummary?.requiresSignIn ?? requireSignIn) ? 'Required' : 'Not required'}</Text>
                    </div>
                    <div className={styles.exchangeSuccessActions}>
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
                    {copyLinkStatus === 'copied' && <Text size={200}>Link copied</Text>}
                    {copyLinkStatus === 'failed' && <Text size={200}>Could not copy link</Text>}
                </div>
            ) : (
                <div className={styles.dialogContentContainer}>
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
                open={isDialogOpen} onOpenChange={onDialogOpenChange}
        >
            <DialogTrigger disableButtonEnhancement>
                <ExchangeInitiationDialogTrigger
                    onRequestingDocumentsChange={handleRequestingDocumentsChange}
                    onChooseBlueprint={openBlueprintPicker}
                />
            </DialogTrigger>
            <DialogSurface className={styles.dialog}>
                <DialogBody>
                    <DialogTitle className={styles.dialogTitle}>
                        {saveBlueprintDialogOpen ? (
                            <div className={styles.saveBlueprintBackRow}>
                                <Button
                                    id={"exchange-save-blueprint-back-btn"}
                                    appearance="subtle"
                                    shape="circular"
                                    size="small"
                                    icon={<ArrowLeftRegular/>}
                                    onClick={closeSaveBlueprintPanel}
                                    aria-label="Back to exchange"
                                />
                                <Text weight="semibold" size={500}>Save as Blueprint</Text>
                            </div>
                        ) : (
                            <>
                                <ExchangeInitiationDialogTitleSection
                                    exchangeInitiatedSuccessfully={exchangeInitiatedSuccessfully}
                                    requestingDocuments={requestingDocuments}
                                    choosingBlueprint={choosingBlueprint}
                                    selectedBlueprintName={selectedBlueprintName}
                                    selectedTab={selectedTab}
                                    showFieldsTab={eligibleSchemas.length > 0}
                                    onTabSelect={(_, data) =>
                                    {
                                        setMessageGroupMessages([]);
                                        setSelectedTab(data.value);
                                    }}
                                    onSaveAsBlueprint={openSaveBlueprintPanel}
                                />
                                {renderErrorMessageBar()}
                            </>
                        )}
                    </DialogTitle>
                    <DialogContent className={styles.dialogContent}>
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
                                    participants={internalParticipants ?? []}
                                    schemaDefinitionId={schemaDefinitionId || undefined}
                                    fieldDefaults={buildBlueprintFieldDefaults(schemaDefinitionId, fieldBindings, fieldValueMap)}
                                />
                            ) : renderDialogContent()}
                        </div>
                    </DialogContent>
                    {!saveBlueprintDialogOpen && (
                        <DialogActions>
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
                    <Toaster inline toasterId={toasterId} position="bottom"/>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default ExchangeInitiation;
