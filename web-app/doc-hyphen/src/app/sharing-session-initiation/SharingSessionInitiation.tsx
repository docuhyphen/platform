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
import useToken from "../../context/useToken.tsx";
import {initiateSharingSession} from "../../services/api.ts";
import useSharingSessionState from './hooks/useSharingSessionState.ts';
import {handleCheckboxChange, handleDocumentChange, handleInputChange} from './components/formHandlers.tsx';
import SharingDocumentsTab from "./components/SessionDocumentsTab.tsx";
import SessionDetailsTab from "./components/SessionDetailsTab.tsx";
import SharingOptionsTab from "./components/SessionOptionsTab.tsx";
import SessionDialogActions from "./components/SessionDialogActions.tsx";
import SessionDialogTrigger from "./components/SessionDialogTrigger.tsx";
import SessionDialogTitleSection from "./components/SessionDialogTitleSection.tsx";
import {DismissRegular} from "@fluentui/react-icons";
import SessionRecipientsTab from "./components/SessionRecipientsTab.tsx";
import {isValidEmail} from "../../utils/helpers.ts";
import {addNewSession} from '../observable/sharingSessionService.ts';
import {useSharingSessionInitiationStyles} from "./SharingSessionInitiationStyles.tsx";
import {SharingSessionRequestDocumentRequest} from "../models/models.tsx";

const SharingSessionInitiation: React.FC = () =>
{
    const styles = useSharingSessionInitiationStyles();
    const token = useToken();
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
        recipientEmail, setRecipientEmail,
        selectedTab, setSelectedTab,
        messageGroupMessages, setMessageGroupMessages,
        requestingDocuments, setRequestingDocuments
    } = useSharingSessionState();

    const toasterId = useId("toasterrr");

    const { dispatchToast } = useToastController(toasterId);

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

    const onInitiateSession = async () => {
        if (initiatingSession)
        {
            return;
        }

        setInitiatingSession(true);

        try {

            if (!recipientEmail || !isValidEmail(recipientEmail))
            {
                //toDo: check if app user isn't sending to themselves
                setMessageGroupMessages(['A valid recipient email is required']);
                setSelectedTab('recipients-tab');
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
                recipientEmail,
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

            const createdSharingSession = await initiateSharingSession(sharingSession, token);

            addNewSession(createdSharingSession);

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

    const onCancelInitiation = () => {
        setSessionInitiatedSuccessfully(false);
        setRecipientEmail('');
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

    return (
        <Dialog modalType="alert">
            <DialogTrigger disableButtonEnhancement>
                <SessionDialogTrigger onRequestingDocumentsChange={handleRequestingDocumentsChange}/>
            </DialogTrigger>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle className={styles.dialogTitle}>
                        <SessionDialogTitleSection
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
                                                    onClick={() => setMessageGroupMessages(messageGroupMessages.filter((_, i) => i !== index))}
                                                    appearance="transparent"
                                                    icon={<DismissRegular/>}/>
                                            }
                                        />
                                    </MessageBar>
                                ))}
                            </MessageBarGroup>
                        }
                    </DialogTitle>
                    <DialogContent>
                        {sessionInitiatedSuccessfully ? (
                            <div className={styles.sharingSessionInitiationSuccess}>
                                <Text size={500}> Sharing Session initiated successfully </Text>
                                <Text size={300}> {sessionName} </Text>
                            </div>
                        ) : (
                            <>
                                {choosingTemplate ? (
                                    <div>Choosing Template</div>
                                ) : (
                                    <div className={styles.sharingSessionInitiationTaps}>
                                        {selectedTab === "recipients-tab" && (
                                            <SessionRecipientsTab
                                                requestingDocuments={requestingDocuments}
                                                recipientEmail={recipientEmail}
                                                setMessageGroupMessages={setMessageGroupMessages}
                                                onRecipientEmailChange={handleInputChange(setRecipientEmail)}
                                            />
                                        )}
                                        {selectedTab === "details-tab" && (
                                            <SessionDetailsTab
                                                sessionName={sessionName}
                                                description={description}
                                                initialShareMessage={initialShareMessage}
                                                onSessionNameChange={handleInputChange(setSessionName)}
                                                onDescriptionChange={handleInputChange(setDescription)}
                                                onInitialShareMessageChange={handleInputChange(setInitialShareMessage)}
                                                setMessageGroupMessages={setMessageGroupMessages}
                                            />
                                        )}
                                        {selectedTab === "documents-tab" && (
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
                                        )}
                                        {selectedTab === "options-tab" && (
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
                                        )}
                                    </div>
                                )}
                            </>
                        )}
                    </DialogContent>
                    <DialogActions>
                        <SessionDialogActions
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