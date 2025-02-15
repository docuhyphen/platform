import {Subject} from 'rxjs';

const sharingSessionsSubject = new Subject<SharingSessionBasicDto>();

export const sharingSessionInitiationObservable = sharingSessionsSubject.asObservable();

export const addNewSession = (session: SharingSessionBasicDto) =>
{
    sharingSessionsSubject.next(session);
};