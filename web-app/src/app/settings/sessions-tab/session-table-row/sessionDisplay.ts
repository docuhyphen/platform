import {UserSessionDto} from "../../../models/models.tsx";

export const formatSessionDate = (iso: string) =>
    new Date(iso).toLocaleString(undefined, {dateStyle: "medium", timeStyle: "short"});

export const getDeviceName = (session: UserSessionDto) =>
    session.deviceName ?? session.userAgent?.split(' ')[0] ?? "Unknown device";
