import {Subject} from 'rxjs';
import {SharingSessionBasicDto, SharingSessionDetailedDto} from "../models/models.tsx";

const sharingSessionsSubject = new Subject<SharingSessionBasicDto>();
const sharingSessionDeletionSubject = new Subject<string>();
const sharingSessionUpdatedSubject = new Subject<SharingSessionDetailedDto>();

export const sharingSessionInitiationObservable = sharingSessionsSubject.asObservable();
export const sharingSessionDeletionObservable = sharingSessionDeletionSubject.asObservable();
export const sharingSessionUpdatedObservable = sharingSessionUpdatedSubject.asObservable();

export const publishNewSharingSessionAddition = (session: SharingSessionBasicDto) => {
    sharingSessionsSubject.next(session);
};

export const publishSharingSessionDelete = (sessionId: string) => {
    sharingSessionDeletionSubject.next(sessionId);
};

export const publishSharingSessionUpdate = (session: SharingSessionDetailedDto) => {
    sharingSessionUpdatedSubject.next(session);
};