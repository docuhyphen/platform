import {InformationRequestReminderResultDto} from "../../../models/models.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import {countedText} from "../operationsLabels.ts";

export const reminderOutcomeSentence = (results: InformationRequestReminderResultDto[]): string =>
{
    const reminded = results.filter(result => !result.cooldownUntil);
    const reopenings = results.flatMap(result => result.cooldownUntil ? [Date.parse(result.cooldownUntil)] : []);
    const notices = reminded.reduce((total, result) => total + result.noticeCount, 0);
    const queued = `${countedText(notices, "reminder notice was", "reminder notices were")} queued for `
        + `${countedText(reminded.length, "request", "requests")}.`;
    if (reopenings.length === 0) return queued;

    return `${queued} ${countedText(reopenings.length, "request was", "requests were")} reminded recently and can be `
        + `reminded again from ${formatInformationRequestTime(Math.max(...reopenings))}.`;
};
