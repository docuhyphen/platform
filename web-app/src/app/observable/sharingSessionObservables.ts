import {Subject} from 'rxjs';
import {
    AppUserDetailedDto,
    SharingSessionBasicDto,
    SharingSessionDetailedDto,
    SharingSessionRequestDocumentRequest
} from "../models/models.tsx";

const sharingSessionsSubject = new Subject<SharingSessionBasicDto>();
const sharingSessionDeletionSubject = new Subject<string>();
const sharingSessionUpdatedSubject = new Subject<SharingSessionDetailedDto>();
const recreateRejectedSessionSubject = new Subject<RecreateRejectedSessionDraft>();

export interface RecreateRejectedSessionDraft
{
    sessionName: string;
    description: string;
    initialShareMessage: string;
    requestRecipientSignIn: boolean;
    allowDocumentAddition: boolean;
    allowDocumentDeletion: boolean;
    allowDocumentDownload: boolean;
    allowDocumentUpdate: boolean;
    allowDocumentUpload: boolean;
    sessionDocuments: SharingSessionRequestDocumentRequest[];
    recipientUser?: AppUserDetailedDto;
    recipientEmail?: string;
    recipientFirstName?: string;
    recipientLastName?: string;
}

export const sharingSessionInitiationObservable = sharingSessionsSubject.asObservable();
export const sharingSessionDeletionObservable = sharingSessionDeletionSubject.asObservable();
export const sharingSessionUpdatedObservable = sharingSessionUpdatedSubject.asObservable();
export const recreateRejectedSessionObservable = recreateRejectedSessionSubject.asObservable();

export const publishNewSharingSessionAddition = (session: SharingSessionBasicDto) =>
{
    sharingSessionsSubject.next(session);
};

export const publishSharingSessionDelete = (sessionId: string) =>
{
    sharingSessionDeletionSubject.next(sessionId);
};

export const publishSharingSessionUpdate = (session: SharingSessionDetailedDto) =>
{
    sharingSessionUpdatedSubject.next(session);
};

export const publishRecreateRejectedSession = (draft: RecreateRejectedSessionDraft) =>
{
    recreateRejectedSessionSubject.next(draft);
};
