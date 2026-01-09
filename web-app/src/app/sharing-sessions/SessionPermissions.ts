import {AppUser, SharingSessionDetailedDto} from "../models/models.tsx";

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
        if (session.initiator?.id === appUser?.id)
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
            permissions.canAddSessionDocument = session.allowDocumentAddition || false;
            permissions.canEditSessionDocument = session.allowDocumentUpdate || false;
            permissions.canDownloadDocumentsZip = session.allowDocumentDownload || false;
        }
    }

    return permissions;
};