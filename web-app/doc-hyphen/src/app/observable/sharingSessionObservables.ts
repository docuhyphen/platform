import {Subject} from 'rxjs';
import {SharingSessionBasicDto} from "../models/models.tsx";

const sharingSessionsSubject = new Subject<SharingSessionBasicDto>();
const sharingSessionDeletionSubject = new Subject<string>();

export const sharingSessionInitiationObservable = sharingSessionsSubject.asObservable();
export const sharingSessionDeletionObservable = sharingSessionDeletionSubject.asObservable();

export const publishNewSharingSessionAddition = (session: SharingSessionBasicDto) => {
    sharingSessionsSubject.next(session);
};

export const publishSharingSessionDelete = (sessionId: string) => {
    sharingSessionDeletionSubject.next(sessionId);
};