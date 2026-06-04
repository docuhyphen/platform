import {useState} from 'react';
import {SharingSessionRequestDocumentRequest} from '../../models/models.tsx';
import {
    SharingSessionInitiationRecipientMode
} from "../components/session-initiation-recipients-tab/SessionInitiationRecipientsTab.tsx";
import {
    SharingSessionNewMainRecipient
} from "../components/session-initiation-recipients-tab/new-recipient/NewRecipient.tsx";
import {SessionShareRole} from '../../../services/types/roles.ts';
import {ShareConstraints} from '../../../services/types/dtos.ts';

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
    const [recipientMode, setRecipientMode] = useState<SharingSessionInitiationRecipientMode>(SharingSessionInitiationRecipientMode.PEOPLE);
    const [recipientOrg, setRecipientOrg] = useState<any>();
    const [recipientOrgUser, setRecipientOrgUser] = useState<any | undefined>();
    const [recipientOrgGroup, setRecipientOrgGroup] = useState<any | undefined>();
    const [internalParticipants, setInternalParticipants] = useState<any | undefined>();
    const [newRecipient, setNewRecipient] = useState<SharingSessionNewMainRecipient | undefined>({
        email: '',
        firstName: '',
        lastName: ''
    });
    // Plan 07 G1 — explicit recipient role + constraints. Undefined = "Auto" (backend
    // auto-derives EDITOR/VIEWER from the allowDocument* flags + no extra constraints).
    const [recipientRole, setRecipientRole] = useState<SessionShareRole | undefined>(undefined);
    const [recipientConstraints, setRecipientConstraints] = useState<ShareConstraints>({});

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
        internalParticipants, setInternalParticipants,
        newRecipient, setNewRecipient,
        recipientRole, setRecipientRole,
        recipientConstraints, setRecipientConstraints,
    };
};

export default useSharingSessionInitiatingState;