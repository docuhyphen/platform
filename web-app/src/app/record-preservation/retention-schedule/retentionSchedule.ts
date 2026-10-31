import {countedText} from "../../information-requests/operations/operationsLabels.ts";
import {RecordRetentionScheduleVersionDto} from "../../models/models.tsx";

const RETENTION_DAYS_LIMIT = 365000;

export interface RetentionFormState {
    minimumDays?: number;
    disposalDays?: number;
    minimumProblem?: string;
    disposalProblem?: string;
    valid: boolean;
}

const wholeDays = (raw: string): number | undefined => {
    const days = /^\d+$/.test(raw.trim()) ? Number(raw.trim()) : undefined;
    return days !== undefined && Number.isSafeInteger(days) && days <= RETENTION_DAYS_LIMIT ? days : undefined;
};

const minimumProblemOf = (minimum: string, minimumDays: number | undefined, disposalGiven: boolean): string | undefined => {
    if (minimum.trim()) return minimumDays === undefined ? "Enter whole days from 0 to 365000" : undefined;
    return disposalGiven ? "Enter the days a finished request is kept at least." : undefined;
};

export const retentionFormState = (minimum: string, disposal: string): RetentionFormState => {
    const minimumDays = wholeDays(minimum);
    const disposalGiven = disposal.trim().length > 0;
    const disposalDays = disposalGiven ? wholeDays(disposal) : undefined;
    const disposalTooShort = disposalDays !== undefined && minimumDays !== undefined && disposalDays < minimumDays;
    const disposalProblem = disposalGiven && (disposalDays === undefined || disposalTooShort)
        ? "Enter whole days no shorter than the minimum, up to 365000"
        : undefined;
    return {
        minimumDays,
        disposalDays,
        minimumProblem: minimumProblemOf(minimum, minimumDays, disposalGiven),
        disposalProblem,
        valid: minimumDays !== undefined && !disposalProblem,
    };
};

export const retentionVersionSentence = (version: RecordRetentionScheduleVersionDto): string =>
    version.disposalAfterDays === undefined || version.disposalAfterDays === null
        ? `Version ${version.versionNumber}: kept at least ${countedText(version.minimumRetentionDays, "day", "days")} after finishing, never disposed automatically.`
        : `Version ${version.versionNumber}: kept at least ${countedText(version.minimumRetentionDays, "day", "days")}, disposed ${countedText(version.disposalAfterDays, "day", "days")} after finishing.`;
