import { useState } from 'react';
import { SharingSessionRequestDocumentRequest } from '../models/models.tsx';

const useSharingSessionState = () => {
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
    const [selectedTab, setSelectedTab] = useState<string>("recipients-tab");

    return {
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
        selectedTab, setSelectedTab
    };
};

export default useSharingSessionState;