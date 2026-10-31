const pad = (value: number): string => String(value).padStart(2, "0");

export const localDateTimeInputValue = (date: Date): string =>
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;

export const epochMillisFromLocalInput = (value: string): number | undefined =>
{
    if (!value) return undefined;
    const time = new Date(value).getTime();
    return Number.isNaN(time) ? undefined : time;
};

export const instantFromLocalInput = (value: string): string | undefined => {
    const time = epochMillisFromLocalInput(value);
    return time === undefined ? undefined : new Date(time).toISOString();
};
