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
    MessageBar,
    MessageBarActions,
    MessageBarBody,
    MessageBarGroup,
    Text,
    Toast,
    Toaster,
    ToastTitle,
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
import {AppUserDetailedDto, OrganizationBasicDto, SharingSessionRequestDocumentRequest} from "../models/models.tsx";
import {OrganizationGroupBasicDto} from "../../services/organizationApi.ts";
import {
    SharingSessionNewMainRecipient
} from "./components/session-initiation-recipients-tab/new-recipient/NewRecipient.tsx";

const SharingSessionInitiation: React.FC = () =>
{
    const styles = useSharingSessionInitiationStyles();
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

    const {dispatchToast} = useToastController(toasterId);

    const showServerErrorToast = (message: string) =>
    {
        dispatchToast(
            <Toast>
                <ToastTitle> {message}</ToastTitle>
            </Toast>, {intent: 'error', timeout: 15000},
        );
    };

    const handleRequestingDocumentsChange = (isRequesting: boolean) =>
    {
        setRequestingDocuments(isRequesting);
    };

    const isRecipientValid = (): boolean =>
    {
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
                break;
            case SharingSessionInitiationRecipientMode.MY_ORG:
                if (!recipientOrgUser)
                {
                    setMessageGroupMessages(['A valid recipient user in your organization is required']);
                    setSelectedTab('recipients-tab');
                    return false;
                }
                break;
            case SharingSessionInitiationRecipientMode.USE_EMAIL:
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

            const sharingSession = {
                sessionName,
                description,
                recipientEmail: newRecipient?.email,
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
                allowDocumentUpload: allowDocumentUpload
            };

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
            restrictType: true
        } as any]);
    };

    const onCancelInitiation = () =>
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
                    <Text size={500}> Sharing Session initiated successfully </Text>
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
        <Dialog modalType="alert">
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
                            initiatingSession={initiatingSession}
                            sessionInitiatedSuccessfully={sessionInitiatedSuccessfully}
                            choosingTemplate={choosingTemplate}
                            onCancelInitiation={onCancelInitiation}
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