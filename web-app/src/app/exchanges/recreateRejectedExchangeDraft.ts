import {DocumentType, ExchangeDetailedDto, ImageType} from '../models/models.tsx';
import {RecreateRejectedExchangeDraft} from '../observable/exchangeObservables.ts';

export const toRecreateRejectedExchangeDraft = (exchange: ExchangeDetailedDto): RecreateRejectedExchangeDraft => ({
    name: exchange.name || '',
    description: exchange.description || '',
    initialShareMessage: exchange.initialShareMessage || '',
    requestRecipientSignIn: !!exchange.requestRecipientSignIn,
    allowDocumentAddition: !!exchange.allowDocumentAddition,
    allowDocumentDeletion: !!exchange.allowDocumentDeletion,
    allowDocumentDownload: !!exchange.allowDocumentDownload,
    allowDocumentUpdate: !!exchange.allowDocumentUpdate,
    allowDocumentUpload: !!exchange.allowDocumentUpload,
    exchangeDocuments: (exchange.documents || []).map(doc => ({
        title: doc.title || '',
        restrictedType: doc.restrictedType as (DocumentType | ImageType) | undefined,
        restrictType: !!doc.restrictType,
    })),
    recipientUser: exchange.recipient,
    recipientEmail: exchange.recipient?.email,
    recipientFirstName: exchange.recipient?.person?.firstName,
    recipientLastName: exchange.recipient?.person?.lastName,
});
