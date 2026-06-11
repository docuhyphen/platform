import {useState} from 'react';
import {ExchangeRequestDocumentRequest} from '../../models/models.tsx';
import {
    ExchangeInitiationRecipientMode
} from "../components/exchange-initiation-recipients-tab/ExchangeInitiationRecipientsTab.tsx";
import {
    ExchangeNewMainRecipient
} from "../components/exchange-initiation-recipients-tab/new-recipient/NewRecipient.tsx";
import {ExchangeShareRole} from '../../../services/types/roles.ts';
import {ShareConstraints} from '../../../services/types/dtos.ts';

const useExchangeInitiatingState = () =>
{
    const [choosingTemplate, setChoosingTemplate] = useState(false);
    const [isInitiating, setIsInitiating] = useState(false);
    const [name, setExchangeName] = useState<string>('');
    const [description, setDescription] = useState<string>('');
    const [initialShareMessage, setInitialShareMessage] = useState<string>('');
    const [requireSignIn, setRequireSignIn] = useState<boolean>(true);
    const [allowDocumentAdditions, setAllowDocumentAdditions] = useState<boolean>(false);
    const [allowDocumentDeletions, setAllowDocumentDeletions] = useState<boolean>(false);
    const [allowDocumentDownload, setAllowDocumentDownload] = useState<boolean>(false);
    const [allowDocumentUpdate, setAllowDocumentUpdate] = useState<boolean>(false);
    const [allowDocumentUpload, setAllowDocumentUpload] = useState<boolean>(false);
    const [initiatingExchange, setInitiatingExchange] = useState<boolean>(false);
    const [exchangeInitiatedSuccessfully, setExchangeInitiatedSuccessfully] = useState<boolean>(false);
    const [documents, setDocuments] = useState<ExchangeRequestDocumentRequest[]>([]);
    const [selectedTab, setSelectedTab] = useState<string>("recipients-tab");
    const [messageGroupMessages, setMessageGroupMessages] = useState<any>([]);
    const [requestingDocuments, setRequestingDocuments] = useState<boolean>(true);
    const [recipientMode, setRecipientMode] = useState<ExchangeInitiationRecipientMode>(ExchangeInitiationRecipientMode.PEOPLE);
    const [recipientOrg, setRecipientOrg] = useState<any>();
    const [recipientOrgUser, setRecipientOrgUser] = useState<any | undefined>();
    const [recipientOrgGroup, setRecipientOrgGroup] = useState<any | undefined>();
    const [internalParticipants, setInternalParticipants] = useState<any | undefined>();
    const [newRecipient, setNewRecipient] = useState<ExchangeNewMainRecipient | undefined>({
        email: '',
        firstName: '',
        lastName: ''
    });
    const [recipientRole, setRecipientRole] = useState<ExchangeShareRole | undefined>(undefined);
    const [recipientConstraints, setRecipientConstraints] = useState<ShareConstraints>({});
    const [allowedDownloadFormats, setAllowedDownloadFormats] = useState<string[] | undefined>(undefined);

    return {
        choosingTemplate, setChoosingTemplate,
        isInitiating, setIsInitiating,
        name, setExchangeName,
        description, setDescription,
        initialShareMessage, setInitialShareMessage,
        requireSignIn, setRequireSignIn,
        allowDocumentAdditions, setAllowDocumentAdditions,
        allowDocumentDeletions, setAllowDocumentDeletions,
        allowDocumentDownload, setAllowDocumentDownload,
        allowDocumentUpdate, setAllowDocumentUpdate,
        allowDocumentUpload, setAllowDocumentUpload,
        initiatingExchange, setInitiatingExchange,
        exchangeInitiatedSuccessfully, setExchangeInitiatedSuccessfully,
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
        allowedDownloadFormats, setAllowedDownloadFormats,
    };
};

export default useExchangeInitiatingState;