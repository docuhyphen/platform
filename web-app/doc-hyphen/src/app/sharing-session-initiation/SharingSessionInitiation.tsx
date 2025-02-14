import React from 'react';
import './SharingSessionInitiation.css';
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
    Toast,
    Toaster,
    ToastTitle,
    useId,
    useToastController,
} from "@fluentui/react-components";
import useToken from "../../context/useToken.tsx";
import {useLocation} from "react-router-dom";
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

const SharingSessionInitiation: React.FC = () =>
{
    const token = useToken();
    const location = useLocation();
    const {
        choosingTemplate, setChoosingTemplate,
        isInitiating, setIsInitiating,
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
    }

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

            if (!recipientEmail)
            {
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

            if (documents.some(doc => doc.restrictType && !doc.restrictedType)) {
                setMessageGroupMessages(['All restricted documents must have a type']);
                setSelectedTab('documents-tab');
                return;
            }



            const sharingSession = {
                sessionName,
                description,
                recipientEmail,
                initialShareMessage,
                sessionDocuments: documents.map((doc, i) => ({
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

            alert("Sharing session initiated successfully");
            // navigate(`/sharing-sessions/${createdSharingSession.id}`);

        } catch (error) {

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
    }

    const onCancelInitiation = () => {
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
    };

    return (
        <Dialog modalType="alert">
            <DialogTrigger disableButtonEnhancement>
                <SessionDialogTrigger onRequestingDocumentsChange={handleRequestingDocumentsChange}/>
            </DialogTrigger>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle id="dialog-title">
                        <SessionDialogTitleSection
                            sessionInitiatedSuccessfully={sessionInitiatedSuccessfully}
                            requestingDocuments={requestingDocuments}
                            choosingTemplate={choosingTemplate}
                            setChoosingTemplate={setChoosingTemplate}
                            selectedTab={selectedTab}
                            onTabSelect={(_, data) =>
                            {
                                setMessageGroupMessages([]);
                                setSelectedTab(data.value)
                            }
                            }
                        />
                        {messageGroupMessages &&
                            <MessageBarGroup id={"error-messages-group"}>
                                {messageGroupMessages.map((message, index) => (
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
                            <div id="sharing-session-initiation-success">
                                <Text size={500}> Sharing Session initiated successfully </Text>
                                <Text size={300}> {sessionName} </Text>
                                <Button appearance={"outline"}> View </Button>
                            </div>
                        ) : (
                            <>
                                {choosingTemplate ? (
                                    <div>Choosing Template</div>
                                ) : (
                                    <div id="sharing-session-initiation-taps">
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
                                                    handleDocumentChange(documents, setDocuments)(index, 'title', value)
                                                }}
                                                onDocumentTypeChange={(index, value) =>
                                                {
                                                    setMessageGroupMessages([]);
                                                    handleDocumentChange(documents, setDocuments)(index, 'restrictedType', value)
                                                }}
                                                onRestrictDocumentTypeChange={(index, ev) =>
                                                {
                                                    setMessageGroupMessages([]);
                                                    handleDocumentChange(documents, setDocuments)(index, 'restrictType', ev.target.checked)
                                                }}
                                                onDeleteDocument={(index) =>
                                                {
                                                    setMessageGroupMessages([]);
                                                    setDocuments(prevDocuments =>
                                                    {
                                                        console.log("prevDocuments", prevDocuments);

                                                        const updatedDocuments = prevDocuments.filter((_, i) => i !== index);

                                                        updatedDocuments.map((doc, i) => ({
                                                            ...doc,
                                                            restrictedType: prevDocuments[i].restrictedType
                                                        }));

                                                        console.log("updatedDocuments", updatedDocuments);

                                                        return updatedDocuments
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