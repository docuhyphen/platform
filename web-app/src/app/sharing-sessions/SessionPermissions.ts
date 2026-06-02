import {SharingSessionDetailedDto, SharingSessionStatus} from "../models/models.tsx";

export interface SharingSessionPermissions
{
    canAddSessionDocument: boolean;
    canUploadDocument: boolean;
    canEditSessionDocument: boolean;
    canDeleteSessionDocument: boolean;
    canEditSharingOptions: boolean;
    canEndSession: boolean;
    canDownloadDocumentsZip: boolean;
    canDeleteSession: boolean,
}

const NO_PERMISSIONS: SharingSessionPermissions = {
    canAddSessionDocument: false,
    canUploadDocument: false,
    canEditSessionDocument: false,
    canDeleteSessionDocument: false,
    canEditSharingOptions: false,
    canEndSession: false,
    canDownloadDocumentsZip: false,
    canDeleteSession: false,
};

/**
 * Resolve effective permissions for `appUser` on `session`. Accepts any user-like object
 * (AppUser or AppUserDetailedDto) as long as it carries an `id`.
 *
 * - Initiator: full permissions while the session is active; only delete after it is terminal.
 * - Recipient: permissions are derived from the session's per-document flags (which the
 *   backend populates from the recipient share's constraints JSON).
 * - No session or no signed-in user: no permissions.
 */
export const getPermissions = (
    session: SharingSessionDetailedDto | null | undefined,
    appUser: { id?: string | null } | null | undefined,
): SharingSessionPermissions =>
{
    if (!session || !appUser?.id)
    {
        return {...NO_PERMISSIONS};
    }

    const permissions: SharingSessionPermissions = {...NO_PERMISSIONS};
    const isInitiator = !!session.initiator?.id && session.initiator.id === appUser.id;
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
        permissions.canUploadDocument = true;
        permissions.canEditSessionDocument = true;
        permissions.canDeleteSessionDocument = true;
        permissions.canEditSharingOptions = true;
        permissions.canEndSession = true;
        permissions.canDownloadDocumentsZip = true;
        permissions.canDeleteSession = true;
    }
    else
    {
        permissions.canAddSessionDocument = !!session.allowDocumentAddition;
        permissions.canUploadDocument = !!session.allowDocumentUpload;
        permissions.canEditSessionDocument = !!session.allowDocumentUpdate;
        permissions.canDeleteSessionDocument = !!session.allowDocumentDeletion;
        permissions.canDownloadDocumentsZip = !!session.allowDocumentDownload;
    }

    return permissions;
};