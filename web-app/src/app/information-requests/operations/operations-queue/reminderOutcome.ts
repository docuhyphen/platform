import {InformationRequestReminderResultDto} from "../../../models/models.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";

const counted = (count: number, one: string, many: string): string => `${count} ${count === 1 ? one : many}`;

export const reminderOutcomeSentence = (results: InformationRequestReminderResultDto[]): string =>
{
    const reminded = results.filter(result => !result.cooldownUntil);
    const reopenings = results.flatMap(result => result.cooldownUntil ? [result.cooldownUntil] : []).sort();
    const notices = reminded.reduce((total, result) => total + result.noticeCount, 0);
    const queued = `${counted(notices, "reminder notice was", "reminder notices were")} queued for `
        + `${counted(reminded.length, "request", "requests")}.`;
    if (reopenings.length === 0) return queued;

    return `${queued} ${counted(reopenings.length, "request was", "requests were")} reminded recently and can be `
        + `reminded again from ${formatInformationRequestTime(reopenings[reopenings.length - 1])}.`;
};
