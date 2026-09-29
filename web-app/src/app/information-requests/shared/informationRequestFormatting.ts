export interface InformationRequestFormatOptions
{
    locale?: string;
    timeZone?: string;
}

const SIZE_UNITS = ["KB", "MB", "GB", "TB"];
const SIZE_STEP = 1024;

export const formatInformationRequestTime = (value: string | undefined, options: InformationRequestFormatOptions = {}): string =>
    value
        ? new Intl.DateTimeFormat(options.locale, {
            dateStyle: "medium",
            timeStyle: "short",
            timeZone: options.timeZone,
        }).formatToParts(new Date(value)).map(part => part.value).join("") + zoneSuffix(value, options)
        : "";

const zoneSuffix = (value: string, options: InformationRequestFormatOptions): string =>
{
    const zone = new Intl.DateTimeFormat(options.locale, {timeZoneName: "short", timeZone: options.timeZone})
        .formatToParts(new Date(value))
        .find(part => part.type === "timeZoneName")?.value;
    return zone ? ` ${zone}` : "";
};

export const formatInformationRequestCount = (value: number, locale?: string): string =>
    new Intl.NumberFormat(locale).format(value);

export const formatInformationRequestSize = (bytes: number, locale?: string): string =>
{
    if (bytes < SIZE_STEP) return `${new Intl.NumberFormat(locale).format(bytes)} bytes`;
    let value = bytes / SIZE_STEP;
    let unit = 0;
    while (value >= SIZE_STEP && unit < SIZE_UNITS.length - 1)
    {
        value /= SIZE_STEP;
        unit += 1;
    }
    return `${new Intl.NumberFormat(locale, {maximumFractionDigits: 1}).format(value)} ${SIZE_UNITS[unit]}`;
};
