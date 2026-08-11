import {useState} from 'react';
import {
    AppUserPublicDto,
    ExchangeRequestDocumentRequest,
    OrganizationBasicDto,
    SchemaFieldBindingDto,
} from '../../models/models.tsx';
import {
    ExchangeInitiationRecipientMode
} from "../components/exchange-initiation-recipients-tab/exchangeInitiationRecipientMode.ts";
import {
    ExchangeNewMainRecipient
} from "../components/exchange-initiation-recipients-tab/new-recipient/NewRecipient.tsx";
import {ExchangeShareRoleName} from '../../../services/types/roles.ts';
import {ShareConstraints} from '../../../services/types/dtos.ts';
import {OrganizationGroupBasicDto} from '../../../services/organizationApi.ts';
import {ExternalIdentityResolution} from '../../../services/organizationTrust.ts';

const useExchangeInitiatingState = () =>
{
    const [choosingBlueprint, setChoosingBlueprint] = useState(false);
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
    const [messageGroupMessages, setMessageGroupMessages] = useState<string[]>([]);
    const [requestingDocuments, setRequestingDocuments] = useState<boolean>(true);
    const [recipientMode, setRecipientMode] = useState<ExchangeInitiationRecipientMode>(ExchangeInitiationRecipientMode.PEOPLE);
    const [recipientOrg, setRecipientOrg] = useState<OrganizationBasicDto>();
    const [recipientOrgUser, setRecipientOrgUser] = useState<AppUserPublicDto>();
    const [recipientOrgGroup, setRecipientOrgGroup] = useState<OrganizationGroupBasicDto>();
    const [recipientResolution, setRecipientResolution] = useState<ExternalIdentityResolution>();
    const [internalParticipants, setInternalParticipants] = useState<AppUserPublicDto[]>([]);
    const [newRecipient, setNewRecipient] = useState<ExchangeNewMainRecipient | undefined>({
        email: '',
        firstName: '',
        lastName: ''
    });
    const [recipientRole, setRecipientRole] = useState<ExchangeShareRoleName | undefined>(undefined);
    const [recipientConstraints, setRecipientConstraints] = useState<ShareConstraints>({});
    const [allowedDownloadFormats, setAllowedDownloadFormats] = useState<string[] | undefined>(undefined);
    const [schemaDefinitionId, setSchemaDefinitionId] = useState<string | undefined>(undefined);
    const [fieldValueMap, setFieldValueMap] = useState<Record<string, unknown>>({});
    const [fieldBindings, setFieldBindings] = useState<SchemaFieldBindingDto[]>([]);

    return {
        choosingBlueprint, setChoosingBlueprint,
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
        recipientResolution, setRecipientResolution,
        internalParticipants, setInternalParticipants,
        newRecipient, setNewRecipient,
        recipientRole, setRecipientRole,
        recipientConstraints, setRecipientConstraints,
        allowedDownloadFormats, setAllowedDownloadFormats,
        schemaDefinitionId, setSchemaDefinitionId,
        fieldValueMap, setFieldValueMap,
        fieldBindings, setFieldBindings,
    };
};

export default useExchangeInitiatingState;
