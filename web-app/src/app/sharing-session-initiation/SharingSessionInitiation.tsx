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

const SharingSessionInitiation: React.FC = () =>
{
    const styles = useSharingSessionInitiationStyles();
    const {appUser} = useAuth();
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
        newRecipient, setNewRecipient
    } = useSharingSessionInitiatingState();

    const toasterId = useId("sharing-session-initiation-toaster");
    const [isDialogOpen, setIsDialogOpen] = React.useState(false);

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
            console.log("initiating session");
            console.log("recipientOrg", recipientOrg);
            console.log("recipientOrgGroup", recipientOrgGroup)
            console.log("recipientOrgUser", recipientOrgUser);
            console.log("internalParticipants", internalParticipants)
            console.log("newRecipient", newRecipient);

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
                participants: internalParticipants
                    ?.filter(p => !appUser || p.id !== appUser.id)
                    ?.map(p => {
                        return {id: p.id, participantType: SharingSessionParticipantType.APP_USER}
                    })
            } as SharingSessionInitiationRequest;

            const createdSharingSession = await initiateSharingSession(sharingSession);

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
        setSessionInitiatedSuccessfully(false);
        setRecipientOrg(null)
        setRecipientOrgUser(null)
        setRecipientOrgGroup(null)
        setSessionName('');
        setDescription('');
        setInitialShareMessage('');
        setRequireSignIn(false);
        setAllowDocumentAdditions(false);
        setAllowDocumentDeletions(false);
        setAllowDocumentDownload(false);
        setAllowDocumentUpdate(false);
        setAllowDocumentUpload(false);
        setDocuments([]);
        setSelectedTab('recipients-tab');
    };

    const onCancelInitiation = () =>
    {
        resetInitiationForm();
        setIsDialogOpen(false);
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
                    <Text size={300} italic={true}> {sessionName} </Text>
                    <Button appearance={"transparent"}>Copy Link</Button>
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
        <Dialog modalType="alert" open={isDialogOpen} onOpenChange={(_, data) => setIsDialogOpen(data.open)}>
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