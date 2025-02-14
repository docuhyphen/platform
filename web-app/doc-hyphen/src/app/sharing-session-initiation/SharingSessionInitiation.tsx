import React, {ChangeEvent, useState} from 'react';
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
    InputOnChangeData,
    SelectTabData,
    SelectTabEvent,
    TabValue,
    Text,
    Toast,
    ToastTitle,
    useId,
    useToastController,
} from "@fluentui/react-components";
import useToken from "../../context/useToken.tsx";
import {useLocation} from "react-router-dom";
import {
    DocumentType,
    ImageType,
    SharingSessionInitiationRequest,
    SharingSessionRequestDocumentRequest
} from "../models/models.tsx";
import {initiateSharingSession} from "../../services/api.ts";
import SharingSessionRecipientsTab from "./components/SessionRecipientsTab.tsx";
import SharingDocumentsTab from "./components/SessionDocumentsTab.tsx";
import SessionDetailsTab from "./components/SessionDetailsTab.tsx";
import SharingOptionsTab from "./components/SessionOptionsTab.tsx";
import SessionDialogActions from "./components/SessionDialogActions.tsx";
import SessionDialogTrigger from "./components/SessionDialogTrigger.tsx";
import SessionDialogTitleSection from "./components/SessionDialogTitleSection.tsx";

const SharingSessionInitiation: React.FC = () => {

    const token = useToken();
    const location = useLocation();

    const [choosingTemplate, setChoosingTemplate] = useState(false);
    const [isInitiating, setIsInitiating] = useState(false);
    const [sessionName, setSessionName] = useState<string>('');
    const [description, setDescription] = useState<string>('');
    const [initialShareMessage, setInitialShareMessage] = useState<string>('');
    const [requireSignIn, setRequireSignIn] = useState<boolean>(true);
    const [allowDocumentAdditions, setAllowDocumentAdditions] = useState<boolean>(false);
    const [allowDocumentDeletions, setAllowDocumentDeletions] = useState<boolean>(false);
    const [allowDocumentDownload, setAllowDocumentDownload] = useState<boolean>(false);
    const [allowDocumentUpdate, setAllowDocumentUpdate] = useState<boolean>(false);
    const [allowDocumentUpload, setAllowDocumentUpload] = useState<boolean>(false);
    const [initiatingSession, setInitiatingSession] = useState<boolean>(false);
    const [sessionInitiatedSuccessfully, setSessionInitiatedSuccessfully] = useState<boolean>(false);
    const [documents, setDocuments] = useState<SharingSessionRequestDocumentRequest[]>([]);
    const [recipientEmail, setRecipientEmail] = useState<string>('');

    const queryParams = new URLSearchParams(location.search);
    const request = queryParams.get('request');

    const toasterId = useId("toaster");
    const { dispatchToast } = useToastController(toasterId);

    const showFormWarningToast = (message: string) => {
        dispatchToast(
            <Toast>
                <ToastTitle> {message}</ToastTitle>
            </Toast>, { intent: 'warning' },
        );
    }

    const [selectedTab, setSelectedTab] = useState<TabValue>("recipients-tab");

    const onTabSelect = (_: SelectTabEvent, data: SelectTabData) => {
        setSelectedTab(data.value);
    };

    const onInitiateSession = async () => {
        if (initiatingSession) {
            return;
        }

        if (!recipientEmail) {
            alert('Recipient email is required');
            return;
        }

        setInitiatingSession(true);

        try {
            const sharingSession: SharingSessionInitiationRequest = {
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

            if (!sessionName && !sessionName.length) {
                showFormWarningToast('Session name is required');
                return;
            }

            let oneDocumentInvalid = documents.some((document) => !document.title);

            if (oneDocumentInvalid) {
                showFormWarningToast('All documents must have names');
                return;
            }

            oneDocumentInvalid = documents.some((document) => {
                return document.restrictType && !document.restrictedType;
            });

            if (oneDocumentInvalid) {
                showFormWarningToast('All restricted documents must have a type');
                return;
            }

            sharingSession?.sessionDocuments?.forEach((document) => {
                document.restrictType = undefined;
            });

            setIsInitiating(true);
            const createdSharingSession = await initiateSharingSession(sharingSession, token);

            alert("Sharing session initiated successfully");
            console.log()
            // navigate(`/sharing-sessions/${createdSharingSession.id}`);

        } catch (error) {
            console.error('Session initiation failed', error);
        } finally {
            setInitiatingSession(false);
            setIsInitiating(false);
        }
    };

    const addNewDocument = () => {
        setDocuments([...documents, {} as SharingSessionRequestDocumentRequest]);
    };

    const onRecipientEmailChange = (_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setRecipientEmail(newValue.value || '')
    }

    const onSessionNameChange = (_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) => {
        setSessionName(newValue.value || '');
    };

    const onDescriptionChange = (_e: ChangeEvent<HTMLTextAreaElement>, newValue: InputOnChangeData) => {
        setDescription(newValue.value || '');
    };

    const onInitialShareMessageChange = (_e: ChangeEvent<HTMLTextAreaElement>, newValue: InputOnChangeData) => {
        setInitialShareMessage(newValue.value || '');
    };

    const onDocumentNameChange = (index: number, newValue: string) => {
        const updatedDocuments = [...documents];
        updatedDocuments[index].title = newValue;
        setDocuments(updatedDocuments);
    };

    const onDocumentTypeChange = (index: number, newType: DocumentType | ImageType) => {
        const updatedDocuments = [...documents];
        updatedDocuments[index].restrictedType = newType;
        setDocuments(updatedDocuments);
    };

    const onRestrictDocumentTypeChange = (index: number, ev: ChangeEvent<HTMLInputElement>) => {
        const updatedDocuments = [...documents];
        updatedDocuments[index].restrictType = ev.target.checked;

        setDocuments(updatedDocuments);
    };

    const onRequireSignInChange = (ev: ChangeEvent<HTMLInputElement>) => {
        setRequireSignIn(ev.target.checked);
    };

    const onAllowDocumentAdditionsChange = (ev: ChangeEvent<HTMLInputElement>) => {
        setAllowDocumentAdditions(ev.target.checked);
    }

    const onAllowDocumentDeletionsChange = (ev: ChangeEvent<HTMLInputElement>) => {
        setAllowDocumentDeletions(ev.target.checked);
    }

    const onAllowDocumentDownloadChange = (ev: ChangeEvent<HTMLInputElement>) => {
        setAllowDocumentDownload(ev.target.checked);
    }

    const onAllowDocumentUpdateChange = (ev: ChangeEvent<HTMLInputElement>) => {
        setAllowDocumentUpdate(ev.target.checked);
    }

    const onAllowDocumentUploadChange = (ev: ChangeEvent<HTMLInputElement>) => {
        setAllowDocumentUpload(ev.target.checked);
    }

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
    }

    const renderSharingDocumentsTabContent = () => (
        <SharingDocumentsTab
            documents={documents}
            onDocumentNameChange={onDocumentNameChange}
            onDocumentTypeChange={onDocumentTypeChange}
            onRestrictDocumentTypeChange={onRestrictDocumentTypeChange}
            onDeleteDocument={(index) =>
            {
                const updatedDocuments = documents.filter((_, docIndex) => docIndex !== index);
                setDocuments(updatedDocuments);
            }}
            addNewDocument={addNewDocument}
        />
    );

    const renderSharingOptionsTabContent = () => (
        <SharingOptionsTab
            requireSignIn={requireSignIn}
            allowDocumentAdditions={allowDocumentAdditions}
            allowDocumentDeletions={allowDocumentDeletions}
            allowDocumentDownload={allowDocumentDownload}
            allowDocumentUpdate={allowDocumentUpdate}
            allowDocumentUpload={allowDocumentUpload}
            onRequireSignInChange={onRequireSignInChange}
            onAllowDocumentAdditionsChange={onAllowDocumentAdditionsChange}
            onAllowDocumentDeletionsChange={onAllowDocumentDeletionsChange}
            onAllowDocumentDownloadChange={onAllowDocumentDownloadChange}
            onAllowDocumentUpdateChange={onAllowDocumentUpdateChange}
            onAllowDocumentUploadChange={onAllowDocumentUploadChange}
        />
    );

    const renderSessionDetailsTapContent = () => (
        <SessionDetailsTab
            sessionName={sessionName}
            description={description}
            initialShareMessage={initialShareMessage}
            onSessionNameChange={onSessionNameChange}
            onDescriptionChange={onDescriptionChange}
            onInitialShareMessageChange={onInitialShareMessageChange}
        />
    );

    const renderSessionRecipientsTabContent = () => (
        <SharingSessionRecipientsTab
            recipientEmail={recipientEmail}
            onRecipientEmailChange={onRecipientEmailChange}
            request={request}
        />
    )

    const renderSessionTemplateSelection = () => (
        <div>
            Choosing Template
        </div>
    )

    const renderDialogActions = () => (
        <SessionDialogActions
            initiatingSession={initiatingSession}
            sessionInitiatedSuccessfully={sessionInitiatedSuccessfully}
            choosingTemplate={choosingTemplate}
            onCancelInitiation={onCancelInitiation}
            onInitiateSession={onInitiateSession}
        />
    )

    return (
        <Dialog modalType="alert">
            <DialogTrigger disableButtonEnhancement>
                <SessionDialogTrigger/>
            </DialogTrigger>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle id="dialog-title">
                        <SessionDialogTitleSection
                            sessionInitiatedSuccessfully={sessionInitiatedSuccessfully}
                            choosingTemplate={choosingTemplate}
                            setChoosingTemplate={setChoosingTemplate}
                            selectedTab={selectedTab}
                            onTabSelect={onTabSelect}
                        />

                    </DialogTitle>
                    <DialogContent>
                        {sessionInitiatedSuccessfully &&
                            <div id="sharing-session-initiation-success">
                                    <Text size={500}> Sharing Session initiated successfully </Text>
                                    <Text size={300}> {sessionName} </Text>
                                    <Button appearance={"outline"}> View </Button>
                            </div>
                        }
                        {!sessionInitiatedSuccessfully &&
                            <>
                                {choosingTemplate && renderSessionTemplateSelection()}
                                {!choosingTemplate &&
                                    <div id="sharing-session-initiation-taps">
                                        {selectedTab === "recipients-tab" && renderSessionRecipientsTabContent()}
                                        {selectedTab === "details-tab" && renderSessionDetailsTapContent()}
                                        {selectedTab === "documents-tab" && renderSharingDocumentsTabContent()}
                                        {selectedTab === "options-tab" && renderSharingOptionsTabContent()}
                                    </div>
                                }
                            </>
                        }
                    </DialogContent>
                    <DialogActions>
                        {renderDialogActions()}
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default SharingSessionInitiation;