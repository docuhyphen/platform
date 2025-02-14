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
import SharingSessionRecipientsTab from "./components/SessionRecipientsTab.tsx";
import SharingDocumentsTab from "./components/SessionDocumentsTab.tsx";
import SessionDetailsTab from "./components/SessionDetailsTab.tsx";
import SharingOptionsTab from "./components/SessionOptionsTab.tsx";
import SessionDialogActions from "./components/SessionDialogActions.tsx";
import SessionDialogTrigger from "./components/SessionDialogTrigger.tsx";
import SessionDialogTitleSection from "./components/SessionDialogTitleSection.tsx";
import {DismissRegular} from "@fluentui/react-icons";

const SharingSessionInitiation: React.FC = () => {
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
    } = useSharingSessionState();

    const queryParams = new URLSearchParams(location.search);
    const request = queryParams.get('request');

    const toasterId = useId("toasterrr");

    const { dispatchToast } = useToastController(toasterId);

    const showFormWarningToast = (message: string) => {
        dispatchToast(
            <Toast>
                <ToastTitle> {message}</ToastTitle>
            </Toast>, {intent: 'warning', timeout: 15000},
        );
    }

    const onInitiateSession = async () => {

        if (initiatingSession) return;

        setInitiatingSession(true);

        try {
            const sharingSession = {
                sessionName,
                description,
                recipientEmail,
                initialShareMessage,
                sessionDocuments: documents,
                requestRecipientSignIn: requireSignIn,
                allowDocumentAddition: allowDocumentAdditions,
                allowDocumentDeletion: allowDocumentDeletions,
                allowDocumentDownload: allowDocumentDownload,
                allowDocumentUpdate: allowDocumentUpdate,
                allowDocumentUpload: allowDocumentUpload
            };

            if (!sessionName) {

                setMessageGroupMessages(['Session name is required']);
                setSelectedTab('details-tab');
                return;
            }

            if (documents.some(doc => !doc.title)) {
                showFormWarningToast('All documents must have names');
                return;
            }

            if (documents.some(doc => doc.restrictType && !doc.restrictedType)) {
                showFormWarningToast('All restricted documents must have a type');
                return;
            }

            sharingSession.sessionDocuments.forEach(doc => doc.restrictType = undefined);

            setIsInitiating(true);
            const createdSharingSession = await initiateSharingSession(sharingSession, token);

            alert("Sharing session initiated successfully");
            // navigate(`/sharing-sessions/${createdSharingSession.id}`);

        } catch (error) {
            console.error('Session initiation failed', error);
        } finally {
            setInitiatingSession(false);
            setIsInitiating(false);
        }
    };

    const addNewDocument = () => setDocuments([...documents, {} as any]);

    const onCancelInitiation = () => {
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
                <SessionDialogTrigger />
            </DialogTrigger>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle id="dialog-title">
                        <SessionDialogTitleSection
                            sessionInitiatedSuccessfully={sessionInitiatedSuccessfully}
                            choosingTemplate={choosingTemplate}
                            setChoosingTemplate={setChoosingTemplate}
                            selectedTab={selectedTab}
                            onTabSelect={(_, data) => setSelectedTab(data.value)}
                        />
                        {messageGroupMessages &&
                            <MessageBarGroup>
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
                                            <SharingSessionRecipientsTab
                                                recipientEmail={recipientEmail}
                                                onRecipientEmailChange={handleInputChange(setRecipientEmail)}
                                                request={request}
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
                                            />
                                        )}
                                        {selectedTab === "documents-tab" && (
                                            <SharingDocumentsTab
                                                documents={documents}
                                                onDocumentNameChange={(index, value) => handleDocumentChange(documents, setDocuments)(index, 'title', value)}
                                                onDocumentTypeChange={(index, value) => handleDocumentChange(documents, setDocuments)(index, 'restrictedType', value)}
                                                onRestrictDocumentTypeChange={(index, ev) => handleDocumentChange(documents, setDocuments)(index, 'restrictType', ev.target.checked)}
                                                onDeleteDocument={(index) => setDocuments(documents.filter((_, i) => i !== index))}
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
                            onInitiateSession={onInitiateSession}
                        />
                    </DialogActions>
                    <Toaster inline toasterId={toasterId} position="bottom"/>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default SharingSessionInitiation;