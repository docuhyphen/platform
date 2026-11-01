import {AppUserDetailedDto, DocumentDetailedDto, ExchangeDetailedDto, ExchangeStatus} from "../../../models/models";

export interface PreviewDocumentAccess
{
    downloadAllowed: boolean;
    canDownloadOriginal: boolean;
    canDownloadAsPdf: boolean;
    showDownloadDisabledNotice: boolean;
    watermarkText: string | undefined;
    isArchivedExchange: boolean;
}

export const resolvePreviewDocumentAccess = (
    exchange: ExchangeDetailedDto,
    exchangeDocument: DocumentDetailedDto,
    appUser: AppUserDetailedDto | null,
): PreviewDocumentAccess =>
{
    const downloadAllowed = exchange?.allowDocumentDownload !== false;
    const canDownloadOriginal =
        downloadAllowed &&
        (!exchange?.allowedDownloadFormats ||
         exchange.allowedDownloadFormats.includes(exchangeDocument?.type ?? ''));
    const canDownloadAsPdf = downloadAllowed && exchangeDocument?.type !== 'PDF';
    const watermarkText = exchange?.watermark
        ? (appUser?.email || 'CONFIDENTIAL').toUpperCase()
        : undefined;
    const isArchivedExchange = exchange?.status === ExchangeStatus.ENDED
        || exchange?.status === ExchangeStatus.REJECTED
        || exchange?.status === ExchangeStatus.RESCINDED;

    return {
        downloadAllowed,
        canDownloadOriginal,
        canDownloadAsPdf,
        showDownloadDisabledNotice: appUser.id != exchange.initiator.id && !downloadAllowed,
        watermarkText,
        isArchivedExchange,
    };
};
