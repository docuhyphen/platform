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
import {DismissRegular} from "@fluentui/react-icons";
import ExchangeInitiationRecipientsTab, {
    ExchangeInitiationRecipientMode
} from "./components/exchange-initiation-recipients-tab/ExchangeInitiationRecipientsTab.tsx";
import {publishNewExchangeAddition} from '../observable/exchangeObservables.ts';
import {useExchangeInitiationStyles} from "./ExchangeInitiationStyles.tsx";
import {
    ExchangeInitiationRequest, ExchangeParticipantRole, ExchangeParticipantType,
    ExchangeRequestDocumentRequest
} from "../models/models.tsx";
import {useAuth} from "../../context/AuthContext.tsx";
import {recreateRejectedExchangeObservable} from "../observable/exchangeObservables.ts";
import {useNavigate} from "react-router-dom";

type CreatedExchangeSummary = {
    id?: string;
    name: string;
    recipient: string;
    documents: number;
    requiresSignIn: boolean;
    shareLink?: string;
};

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
        internalParticipants, setInternalParticipants,
        newRecipient, setNewRecipient,
        recipientRole, setRecipientRole,
        recipientConstraints, setRecipientConstraints,
        allowedDownloadFormats, setAllowedDownloadFormats,
    } = useExchangeInitiatingState();

    const toasterId = useId("exchange-initiation-toaster");
    const [isDialogOpen, setIsDialogOpen] = React.useState(false);
    const [createdExchangeSummary, setCreatedExchangeSummary] = React.useState<CreatedExchangeSummary | null>(null);
    const [copyLinkStatus, setCopyLinkStatus] = React.useState<'idle' | 'copied' | 'failed'>('idle');

    const {dispatchToast} = useToastController(toasterId);

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

    const buildRecipientLabel = (): string =>
    {
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
        catch (_)
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
            case ExchangeInitiationRecipientMode.EXTERNAL_ORG:
                if (!recipientOrg)
                {
                    setMessageGroupMessages(['A valid recipient organization is required']);
                    setSelectedTab('recipients-tab');
                    return false;
                }
                if (!recipientOrgUser && !recipientOrgGroup)
                {
                    setMessageGroupMessages(['A valid recipient organization user or group is required']);
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

            let recipientType = "EMAIL";

            if (recipientOrgGroup)
            {
                recipientType = "GROUP"
            }
            else if (recipientOrgUser)
            {
                recipientType = "APP_USER"

            }

            const exchange = {
                name,
                description,
                recipientOrgGroupId: recipientOrgGroup?.id,
                recipientAppUserId: recipientOrgUser?.id,
                recipientEmail: newRecipient?.email,
                recipientFirstName: newRecipient?.firstName,
                recipientLastName: newRecipient?.lastName,
                initialShareMessage,
                exchangeDocuments: documents.map((doc: ExchangeRequestDocumentRequest, _: number) => ({
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
                recipientType,
                recipientRoleName: recipientRole,
                recipientConstraintsJson:
                    Object.keys(recipientConstraints).length > 0
                        ? JSON.stringify(recipientConstraints)
                        : undefined,
                participants: internalParticipants
                    ?.filter(p => !appUser || p.id !== appUser.id)
                    ?.map(p => {
                        return {id: p.id, participantType: ExchangeParticipantType.APP_USER}
                    })
            } as ExchangeInitiationRequest;

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
            let errorMessage = error.response?.data || error.message;

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
            restrictedType: 'PDF',
            restrictType: false
        } as any]);
    };

    const resetInitiationForm = () =>
    {
        setChoosingBlueprint(false);
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
        if (!data.open)
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
    }, []);

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
                onDeleteDocument={(index) =>
                {
                    setMessageGroupMessages([]);
                    setDocuments(prevDocuments =>
                    {
                        const updatedDocuments = prevDocuments.filter((_, i) => i !== index);
                        return updatedDocuments;
                    });
                }}
                addNewDocument={addNewDocument}
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
            />
        )
    }

    const renderTabs = () =>
    {
        return (
            <div className={styles.exchangeInitiationTaps}>
                {selectedTab === "recipients-tab" && renderRecipientsTab()}
                {selectedTab === "details-tab" && renderDetailsTab()}
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
                                        onClick={() => onCloseMessageBar(index)}
                                        appearance="transparent"
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
                            appearance={"subtle"}
                            shape={"circular"}
                            onClick={onCopyExchangeLink}
                            disabled={!createdExchangeSummary?.shareLink}
                        >
                            Copy Link
                        </Button>
                        <Button
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
                        <div>Choosing Blueprint</div>
                    ) : renderTabs()}
                </div>
            )}
        </>
    }

    return (
        <Dialog modalType="alert" open={isDialogOpen} onOpenChange={onDialogOpenChange}>
            <DialogTrigger disableButtonEnhancement>
                <ExchangeInitiationDialogTrigger onRequestingDocumentsChange={handleRequestingDocumentsChange}/>
            </DialogTrigger>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle className={styles.dialogTitle}>
                        <ExchangeInitiationDialogTitleSection
                            exchangeInitiatedSuccessfully={exchangeInitiatedSuccessfully}
                            requestingDocuments={requestingDocuments}
                            choosingBlueprint={choosingBlueprint}
                            setChoosingBlueprint={setChoosingBlueprint}
                            selectedTab={selectedTab}
                            onTabSelect={(_, data) =>
                            {
                                setMessageGroupMessages([]);
                                setSelectedTab(data.value);
                            }}
                        />
                        {renderErrorMessageBar()}
                    </DialogTitle>
                    <DialogContent>
                        {renderDialogContent()}
                    </DialogContent>
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
                    <Toaster inline toasterId={toasterId} position="bottom"/>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default ExchangeInitiation;