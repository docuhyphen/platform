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
import {initiateSharingSession} from "../../services/sharingSessionApi.ts";
import useSharingSessionInitiatingState from './hooks/useSharingSessionInitiatingState.ts';
import {handleCheckboxChange, handleDocumentChange, handleInputChange} from './formHandlers.tsx';
import SharingDocumentsTab from "./components/session-initiation-documents-tab/SessionInitiationDocumentsTab.tsx";
import SessionInitiationDetailsTab from "./components/session-initiation-details-tab/SessionInitiationDetailsTab.tsx";
import SharingOptionsTab from "./components/session-initiation-options-tab/SessionInitiationOptionsTab.tsx";
import SessionInitiationDialogActions
    from "./components/session-initiation-dialog-actions/SessionInitiationDialogActions.tsx";
import SessionInitiationDialogTrigger
    from "./components/session-initiation-dialog-trigger/SessionInitiationDialogTrigger.tsx";
import SessionInitiationDialogTitleSection
    from "./components/session-initiation-dialog-title-section/SessionInitiationDialogTitleSection.tsx";
import {DismissRegular} from "@fluentui/react-icons";
import SessionInitiationRecipientsTab, {
    SharingSessionInitiationRecipientMode
} from "./components/session-initiation-recipients-tab/SessionInitiationRecipientsTab.tsx";
import {publishNewSharingSessionAddition} from '../observable/sharingSessionObservables.ts';
import {useSharingSessionInitiationStyles} from "./SharingSessionInitiationStyles.tsx";
import {
    SharingSessionInitiationRequest, SharingSessionParticipantRole, SharingSessionParticipantType,
    SharingSessionRequestDocumentRequest
} from "../models/models.tsx";
import {useAuth} from "../../context/AuthContext.tsx";
import {recreateRejectedSessionObservable} from "../observable/sharingSessionObservables.ts";
import {useNavigate} from "react-router-dom";

type CreatedSessionSummary = {
    id?: string;
    sessionName: string;
    recipient: string;
    documents: number;
    requiresSignIn: boolean;
    shareLink?: string;
};

const SharingSessionInitiation: React.FC = () =>
{
    const styles = useSharingSessionInitiationStyles();
    const {appUser} = useAuth();
    const navigate = useNavigate();
    const {
        choosingTemplate, setChoosingTemplate,
        sessionName, setSessionName,
        description, setDescription,
        initialShareMessage, setInitialShareMessage,
        requireSignIn, setRequireSignIn,
        allowDocumentAdditions, setAllowDocumentAdditions,
        allowDocumentDeletions, setAllowDocumentDeletions,
        allowDocumentDownload, setAllowDocumentDownload,
        allowDocumentUpdate, setAllowDocumentUpdate,
        allowDocumentUpload, setAllowDocumentUpload,
        initiatingSession, setInitiatingSession,
        sessionInitiatedSuccessfully, setSessionInitiatedSuccessfully,
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
    } = useSharingSessionInitiatingState();

    const toasterId = useId("sharing-session-initiation-toaster");
    const [isDialogOpen, setIsDialogOpen] = React.useState(false);
    const [createdSessionSummary, setCreatedSessionSummary] = React.useState<CreatedSessionSummary | null>(null);
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

    const handleRequestingDocumentsChange = (isRequesting: boolean) =>
    {
        setRequestingDocuments(isRequesting);
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

    const onCopySessionLink = async () =>
    {
        const link = createdSessionSummary?.shareLink;
        if (!link)
        {
            setCopyLinkStatus('failed');
            return;
        }

        const copied = await copyTextWithFallback(link);
        setCopyLinkStatus(copied ? 'copied' : 'failed');
    };

    const onViewSession = () =>
    {
        if (!createdSessionSummary?.id)
        {
            return;
        }
        setIsDialogOpen(false);
        navigate(`/sharing-sessions?s=${encodeURIComponent(createdSessionSummary.id)}`);
    };

    const buildSessionShareLink = (sessionId: string, requiresSignIn: boolean): string =>
    {
        const encodedSessionId = encodeURIComponent(sessionId);
        const route = requiresSignIn ? '/sharing-sessions' : '/nas';
        return `${window.location.origin}${route}?s=${encodedSessionId}`;
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
                setMessageGroupMessages(['You cannot be the recipient of your own sharing session']);
                setSelectedTab('recipients-tab');
                return false;
            }
            return true;
        };

        switch (recipientMode)
        {
            case SharingSessionInitiationRecipientMode.EXTERNAL_ORG:
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
                    setMessageGroupMessages(['You cannot be the recipient of your own sharing session']);
                    setSelectedTab('recipients-tab');
                    return false;
                }
                break;
            case SharingSessionInitiationRecipientMode.MY_ORG:
                if (!recipientOrgUser && !recipientOrgGroup)
                {
                    setMessageGroupMessages(['A valid recipient user or group in your organization is required']);
                    setSelectedTab('recipients-tab');
                    return false;
                }
                if (recipientOrgUser && appUser && recipientOrgUser.id === appUser.id)
                {
                    setMessageGroupMessages(['You cannot be the recipient of your own sharing session']);
                    setSelectedTab('recipients-tab');
                    return false;
                }
                break;
            case SharingSessionInitiationRecipientMode.MY_GROUPS:
                if (!recipientOrgGroup)
                {
                    setMessageGroupMessages(['Please select one of your personal groups as the recipient']);
                    setSelectedTab('recipients-tab');
                    return false;
                }
                break;
            case SharingSessionInitiationRecipientMode.PEOPLE:
                // PEOPLE produces either a selected real user (recipientOrgUser) or an
                // email-based new recipient (newRecipient). Validate whichever was set.
                if (recipientOrgUser)
                {
                    if (appUser && recipientOrgUser.id === appUser.id)
                    {
                        setMessageGroupMessages(['You cannot be the recipient of your own sharing session']);
                        setSelectedTab('recipients-tab');
                        return false;
                    }
                    break;
                }
                if (!validateEmailRecipient()) return false;
                break;
            case SharingSessionInitiationRecipientMode.EMAIL:
                if (!validateEmailRecipient()) return false;
                break;
        }
        return true;
    };

    const onInitiateSession = async () =>
    {

        if (initiatingSession)
        {
            return;
        }

        setMessageGroupMessages([]);
        setInitiatingSession(true);

        try
        {
            if (!isRecipientValid())
            {
                return;
            }

            if (!sessionName)
            {
                setMessageGroupMessages(['Session name is required']);
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

            const sharingSession = {
                sessionName,
                description,
                recipientOrgGroupId: recipientOrgGroup?.id,
                recipientAppUserId: recipientOrgUser?.id,
                recipientEmail: newRecipient?.email,
                recipientFirstName: newRecipient?.firstName,
                recipientLastName: newRecipient?.lastName,
                initialShareMessage,
                sessionDocuments: documents.map((doc: SharingSessionRequestDocumentRequest, _: number) => ({
                    ...doc,
                    restrictedType: doc.restrictType ? doc.restrictedType : undefined
                })),
                requestRecipientSignIn: requireSignIn,
                allowDocumentAddition: allowDocumentAdditions,
                allowDocumentDeletion: allowDocumentDeletions,
                allowDocumentDownload: allowDocumentDownload,
                allowDocumentUpdate: allowDocumentUpdate,
                allowDocumentUpload: allowDocumentUpload,
                recipientType,
                recipientRoleName: recipientRole,
                recipientConstraintsJson:
                    Object.keys(recipientConstraints).length > 0
                        ? JSON.stringify(recipientConstraints)
                        : undefined,
                participants: internalParticipants
                    ?.filter(p => !appUser || p.id !== appUser.id)
                    ?.map(p => {
                        return {id: p.id, participantType: SharingSessionParticipantType.APP_USER}
                    })
            } as SharingSessionInitiationRequest;

            const createdSharingSession = await initiateSharingSession(sharingSession);
            const createdSessionId = (createdSharingSession as { id?: string })?.id;
            const shareLink = createdSessionId ? buildSessionShareLink(createdSessionId, requireSignIn) : undefined;

            setCreatedSessionSummary({
                id: createdSessionId,
                sessionName: sessionName.trim(),
                recipient: buildRecipientLabel(),
                documents: documents.length,
                requiresSignIn: requireSignIn,
                shareLink,
            });
            setCopyLinkStatus('idle');

            publishNewSharingSessionAddition(createdSharingSession);

            setSessionInitiatedSuccessfully(true);
        }
        catch (error)
        {
            let errorMessage = error.response?.data || error.message;

            if (!errorMessage)
            {
                errorMessage = "An error unknown occurred while initiating sharing session";
            }

            showServerErrorToast(errorMessage);

        }
        finally
        {
            setInitiatingSession(false);
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
        setChoosingTemplate(false);
        setMessageGroupMessages([]);
        setInitiatingSession(false);
        setSessionInitiatedSuccessfully(false);
        setRecipientOrg(null)
        setRecipientOrgUser(null)
        setRecipientOrgGroup(null)
        setRecipientMode(SharingSessionInitiationRecipientMode.PEOPLE);
        setInternalParticipants(undefined);
        setNewRecipient({
            email: '',
            firstName: '',
            lastName: '',
        });
        setSessionName('');
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
        setCreatedSessionSummary(null);
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
        const subscription = recreateRejectedSessionObservable.subscribe(draft =>
        {
            setChoosingTemplate(false);
            setMessageGroupMessages([]);
            setSessionInitiatedSuccessfully(false);
            setInitiatingSession(false);

            setSessionName(draft.sessionName || '');
            setDescription(draft.description || '');
            setInitialShareMessage(draft.initialShareMessage || '');
            setRequireSignIn(!!draft.requestRecipientSignIn);
            setAllowDocumentAdditions(!!draft.allowDocumentAddition);
            setAllowDocumentDeletions(!!draft.allowDocumentDeletion);
            setAllowDocumentDownload(!!draft.allowDocumentDownload);
            setAllowDocumentUpdate(!!draft.allowDocumentUpdate);
            setAllowDocumentUpload(!!draft.allowDocumentUpload);
            setDocuments(draft.sessionDocuments || []);
            setSelectedTab('details-tab');
            setRecipientOrg(undefined);
            setRecipientOrgGroup(undefined);

            if (draft.recipientUser)
            {
                setRecipientMode(SharingSessionInitiationRecipientMode.PEOPLE);
                setRecipientOrgUser(draft.recipientUser);
                setNewRecipient({
                    email: '',
                    firstName: '',
                    lastName: '',
                });
            }
            else
            {
                setRecipientMode(SharingSessionInitiationRecipientMode.PEOPLE);
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
            <SessionInitiationRecipientsTab
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
            <SessionInitiationDetailsTab
                sessionName={sessionName}
                description={description}
                initialShareMessage={initialShareMessage}
                onSessionNameChange={handleInputChange(setSessionName)}
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
            />
        )
    }

    const renderTabs = () =>
    {
        return (
            <div className={styles.sharingSessionInitiationTaps}>
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
            {sessionInitiatedSuccessfully ? (
                <div className={styles.sharingSessionInitiationSuccess}>
                    <Text size={500}> Sharing Session started successfully </Text>
                    <Text size={300} italic={true}> {createdSessionSummary?.sessionName || sessionName} </Text>
                    <div className={styles.sharingSessionSuccessDetails}>
                        <Text size={200}>Recipient</Text>
                        <Text size={200}>{createdSessionSummary?.recipient || '-'}</Text>
                        <Text size={200}>Documents</Text>
                        <Text size={200}>{createdSessionSummary?.documents ?? documents.length}</Text>
                        <Text size={200}>Recipient sign-in</Text>
                        <Text size={200}>{(createdSessionSummary?.requiresSignIn ?? requireSignIn) ? 'Required' : 'Not required'}</Text>
                    </div>
                    <div className={styles.sharingSessionSuccessActions}>
                        <Button
                            appearance={"subtle"}
                            shape={"circular"}
                            onClick={onCopySessionLink}
                            disabled={!createdSessionSummary?.shareLink}
                        >
                            Copy Link
                        </Button>
                        <Button
                            shape={"circular"}
                            appearance={"subtle"}
                            onClick={onViewSession}
                            disabled={!createdSessionSummary?.id}
                        >
                            View Session
                        </Button>
                    </div>
                    {copyLinkStatus === 'copied' && <Text size={200}>Link copied</Text>}
                    {copyLinkStatus === 'failed' && <Text size={200}>Could not copy link</Text>}
                </div>
            ) : (
                <div className={styles.dialogContentContainer}>
                    {choosingTemplate ? (
                        <div>Choosing Template</div>
                    ) : renderTabs()}
                </div>
            )}
        </>
    }

    return (
        <Dialog modalType="alert" open={isDialogOpen} onOpenChange={onDialogOpenChange}>
            <DialogTrigger disableButtonEnhancement>
                <SessionInitiationDialogTrigger onRequestingDocumentsChange={handleRequestingDocumentsChange}/>
            </DialogTrigger>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle className={styles.dialogTitle}>
                        <SessionInitiationDialogTitleSection
                            sessionInitiatedSuccessfully={sessionInitiatedSuccessfully}
                            requestingDocuments={requestingDocuments}
                            choosingTemplate={choosingTemplate}
                            setChoosingTemplate={setChoosingTemplate}
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
                        <SessionInitiationDialogActions
                            requestingDocuments={requestingDocuments}
                            initiatingSession={initiatingSession}
                            sessionInitiatedSuccessfully={sessionInitiatedSuccessfully}
                            choosingTemplate={choosingTemplate}
                            onResetInitiation={resetInitiationForm}
                            onCloseDialog={onCancelInitiation}
                            onInitiateSession={() => onInitiateSession()}
                        />
                    </DialogActions>
                    <Toaster inline toasterId={toasterId} position="bottom"/>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default SharingSessionInitiation;