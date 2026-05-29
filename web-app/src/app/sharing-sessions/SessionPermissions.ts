import {AppUser, SharingSessionDetailedDto, SharingSessionStatus} from "../models/models.tsx";

export interface SharingSessionPermissions
{
    canAddSessionDocument: boolean;
    canEditSessionDocument: boolean;
    canEditSharingOptions: boolean;
    canEndSession: boolean;
    canDownloadDocumentsZip: boolean;
    canDeleteSession: boolean,
}

export const getPermissions = (session: SharingSessionDetailedDto, appUser: AppUser): SharingSessionPermissions =>
{

    const permissions: SharingSessionPermissions = {
        canAddSessionDocument: false,
        canEditSessionDocument: false,
        canEditSharingOptions: false,
        canEndSession: false,
        canDownloadDocumentsZip: false,
        canDeleteSession: false,
    };

    if (session)
    {
        const isInitiator = session.initiator?.id === appUser?.id;
        const isTerminalStatus =
            session.status === SharingSessionStatus.ENDED ||
            session.status === SharingSessionStatus.REJECTED;

        if (isTerminalStatus)
        {
            permissions.canDeleteSession = isInitiator;
            return permissions;
        }

        if (isInitiator)
        {
            permissions.canAddSessionDocument = true;
            permissions.canEditSessionDocument = true;
            permissions.canEditSharingOptions = true;
            permissions.canEndSession = true;
            permissions.canDownloadDocumentsZip = true;
            permissions.canDeleteSession = true;
        }
        else
        {
            // Recipient upload access is controlled by allowDocumentUpload.
            // Keep allowDocumentAddition support for compatibility with existing sessions.
            permissions.canAddSessionDocument =
                session.allowDocumentAddition ||
                session.allowDocumentUpload ||
                false;
            permissions.canEditSessionDocument = session.allowDocumentUpdate || false;
            permissions.canDownloadDocumentsZip = session.allowDocumentDownload || false;
        }
    }

    return permissions;
};