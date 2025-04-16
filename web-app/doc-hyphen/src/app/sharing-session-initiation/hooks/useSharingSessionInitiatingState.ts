import {useState} from 'react';
import {SharingSessionRequestDocumentRequest} from '../../models/models.tsx';
import {
    SharingSessionInitiationRecipientMode
} from "../components/session-initiation-recipients-tab/SessionInitiationRecipientsTab.tsx";
import {
    SharingSessionNewMainRecipient
} from "../components/session-initiation-recipients-tab/new-recipient/NewRecipient.tsx";

const useSharingSessionInitiatingState = () =>
{
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
    const [selectedTab, setSelectedTab] = useState<string>("recipients-tab");
    const [messageGroupMessages, setMessageGroupMessages] = useState<any>([]);
    const [requestingDocuments, setRequestingDocuments] = useState<boolean>(true);
    const [recipientMode, setRecipientMode] = useState<SharingSessionInitiationRecipientMode>(SharingSessionInitiationRecipientMode.EXTERNAL_ORG);
    const [recipientOrg, setRecipientOrg] = useState<any>();
    const [recipientOrgUser, setRecipientOrgUser] = useState<any | undefined>();
    const [recipientOrgGroup, setRecipientOrgGroup] = useState<any | undefined>();
    const [newRecipient, setNewRecipient] = useState<SharingSessionNewMainRecipient | undefined>({
        email: '',
        firstName: '',
        lastName: ''
    });

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
        selectedTab, setSelectedTab,
        messageGroupMessages, setMessageGroupMessages,
        requestingDocuments, setRequestingDocuments,
        recipientMode, setRecipientMode,
        recipientOrg, setRecipientOrg,
        recipientOrgUser, setRecipientOrgUser,
        recipientOrgGroup, setRecipientOrgGroup,
        newRecipient, setNewRecipient
    };
};

export default useSharingSessionInitiatingState;