const pluralize = (value: number, unit: string): string =>
    `${value} ${unit}${value === 1 ? "" : "s"}`;

export const formatMinutesAsDuration = (totalMinutes: number): string =>
{
    if (totalMinutes < 60) return pluralize(totalMinutes, "minute");
    if (totalMinutes % 1_440 === 0) return pluralize(totalMinutes / 1_440, "day");
    if (totalMinutes % 60 === 0) return pluralize(totalMinutes / 60, "hour");

    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;
    return `${pluralize(hours, "hour")} ${pluralize(minutes, "minute")}`;
};

export const formatHoursAsDuration = (totalHours: number): string =>
    formatMinutesAsDuration(totalHours * 60);

export const formatDurationRange = (minimum: string, maximum: string): string =>
    `${minimum} to ${maximum}`;
