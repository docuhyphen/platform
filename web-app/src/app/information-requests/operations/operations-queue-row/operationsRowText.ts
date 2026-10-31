import {
    InformationRequestNoticeDeliveryState,
    InformationRequestOperationsException,
    InformationRequestOperationsRowDto,
} from "../../../models/models.tsx";
import {formatInformationRequestCount} from "../../shared/informationRequestFormatting.ts";
import {
    countedText,
    exceptionLabels,
    exceptionSingularLabels,
    formattedDuration,
    formattedTime,
    noticeCountLabels,
} from "../operationsLabels.ts";

const known = (value?: number | null): value is number => value !== undefined && value !== null;

export const rowTimingText = (row: InformationRequestOperationsRowDto): string => {
    const points = countedText(row.reminderCount, "clock reminder point", "clock reminder points");
    return known(row.nearestDueAt)
        ? `Open ${formattedDuration(row.ageSeconds)}, due ${formattedTime(row.nearestDueAt)}, ${points}`
        : `Open ${formattedDuration(row.ageSeconds)}, ${points}`;
};

export const rowProgressText = (row: InformationRequestOperationsRowDto): string => {
    if (!known(row.issuedAt)) return "Not issued yet";
    return [
        `Issued ${formattedTime(row.issuedAt)}`,
        known(row.firstViewedAt) ? `first viewed ${formattedTime(row.firstViewedAt)}` : "not viewed yet",
        known(row.startedAt) ? `started ${formattedTime(row.startedAt)}` : "not started yet",
    ].join(", ");
};

export const rowNoticeTexts = (row: InformationRequestOperationsRowDto): string[] =>
    Object.values(InformationRequestNoticeDeliveryState)
        .filter(state => (row.noticeCounts[state] ?? 0) > 0)
        .map(state => `${formatInformationRequestCount(row.noticeCounts[state] ?? 0)} ${noticeCountLabels[state]}`);

export const rowExceptionTexts = (row: InformationRequestOperationsRowDto): string[] =>
    Object.values(InformationRequestOperationsException)
        .filter(exception => (row.exceptionCounts[exception] ?? 0) > 0)
        .map(exception => countedText(row.exceptionCounts[exception] ?? 0, exceptionSingularLabels[exception], exceptionLabels[exception]));
