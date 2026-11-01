import {ExchangeDetailedDto, ExchangeStatus} from "../models/models.tsx";

export interface ExchangePermissions
{
    canAddExchangeDocument: boolean;
    canUploadDocument: boolean;
    canEditExchangeDocument: boolean;
    canDeleteExchangeDocument: boolean;
    canEditSharingOptions: boolean;
    canEndExchange: boolean;
    canDownloadDocumentsZip: boolean;
    canDeleteExchange: boolean,
}

const NO_PERMISSIONS: ExchangePermissions = {
    canAddExchangeDocument: false,
    canUploadDocument: false,
    canEditExchangeDocument: false,
    canDeleteExchangeDocument: false,
    canEditSharingOptions: false,
    canEndExchange: false,
    canDownloadDocumentsZip: false,
    canDeleteExchange: false,
};

export const getPermissions = (
    exchange: ExchangeDetailedDto | null | undefined,
    appUser: { id?: string | null } | null | undefined,
): ExchangePermissions =>
{
    if (!exchange || !appUser?.id)
    {
        return {...NO_PERMISSIONS};
    }

    const permissions: ExchangePermissions = {...NO_PERMISSIONS};
    const isInitiator = !!exchange.initiator?.id && exchange.initiator.id === appUser.id;
    const isTerminalStatus =
        exchange.status === ExchangeStatus.ENDED ||
        exchange.status === ExchangeStatus.REJECTED ||
        exchange.status === ExchangeStatus.RESCINDED;

    if (isTerminalStatus)
    {
        permissions.canDeleteExchange = isInitiator;
        return permissions;
    }

    if (isInitiator)
    {
        permissions.canAddExchangeDocument = true;
        permissions.canUploadDocument = true;
        permissions.canEditExchangeDocument = true;
        permissions.canDeleteExchangeDocument = true;
        permissions.canEditSharingOptions = true;
        permissions.canEndExchange = true;
        permissions.canDownloadDocumentsZip = true;
        permissions.canDeleteExchange = true;
    }
    else
    {
        permissions.canAddExchangeDocument = !!exchange.allowDocumentAddition;
        permissions.canUploadDocument = !!exchange.allowDocumentUpload;
        permissions.canEditExchangeDocument = !!exchange.allowDocumentUpdate;
        permissions.canDeleteExchangeDocument = !!exchange.allowDocumentDeletion;
        permissions.canDownloadDocumentsZip = !!exchange.allowDocumentDownload;
    }

    return permissions;
};
