import {Subject} from 'rxjs';
import {SharingSessionBasicDto} from "../models/models.tsx";

const sharingSessionsSubject = new Subject<SharingSessionBasicDto>();

export const sharingSessionInitiationObservable = sharingSessionsSubject.asObservable();

export const addNewSession = (session: SharingSessionBasicDto) =>
{
    sharingSessionsSubject.next(session);
};