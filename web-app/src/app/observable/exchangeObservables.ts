import {Subject} from 'rxjs';
import {
    AppUserDetailedDto,
    ExchangeBasicDto,
    ExchangeDetailedDto,
    ExchangeRequestDocumentRequest
} from "../models/models.tsx";

const exchangesSubject = new Subject<ExchangeBasicDto>();
const exchangeDeletionSubject = new Subject<string>();
const exchangeUpdatedSubject = new Subject<ExchangeDetailedDto>();
const recreateRejectedExchangeSubject = new Subject<RecreateRejectedExchangeDraft>();

export interface RecreateRejectedExchangeDraft
{
    name: string;
    description: string;
    initialShareMessage: string;
    requestRecipientSignIn: boolean;
    allowDocumentAddition: boolean;
    allowDocumentDeletion: boolean;
    allowDocumentDownload: boolean;
    allowDocumentUpdate: boolean;
    allowDocumentUpload: boolean;
    allowedDownloadFormats?: string[];
    exchangeDocuments: ExchangeRequestDocumentRequest[];
    recipientUser?: AppUserDetailedDto;
    recipientEmail?: string;
    recipientFirstName?: string;
    recipientLastName?: string;
}

export const exchangeInitiationObservable = exchangesSubject.asObservable();
export const exchangeDeletionObservable = exchangeDeletionSubject.asObservable();
export const exchangeUpdatedObservable = exchangeUpdatedSubject.asObservable();
export const recreateRejectedExchangeObservable = recreateRejectedExchangeSubject.asObservable();

export const publishNewExchangeAddition = (exchange: ExchangeBasicDto) =>
{
    exchangesSubject.next(exchange);
};

export const publishExchangeDelete = (exchangeId: string) =>
{
    exchangeDeletionSubject.next(exchangeId);
};

export const publishExchangeUpdate = (exchange: ExchangeDetailedDto) =>
{
    exchangeUpdatedSubject.next(exchange);
};

export const publishRecreateRejectedExchange = (draft: RecreateRejectedExchangeDraft) =>
{
    recreateRejectedExchangeSubject.next(draft);
};
