const pad = (value: number): string => String(value).padStart(2, "0");

export const localDateTimeInputValue = (date: Date): string =>
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;

export const instantFromLocalInput = (value: string): string | undefined =>
{
    if (!value) return undefined;
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
};
