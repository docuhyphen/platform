const getOrdinalSuffix = (day: number): string =>
{
    if (day > 3 && day < 21) return 'th';
    switch (day % 10)
    {
        case 1:
            return 'st';
        case 2:
            return 'nd';
        case 3:
            return 'rd';
        default:
            return 'th';
    }
};

/**
 * Formats a date string to 'dd/mm/yyyy' format.
 * @param dateString - The date string to format.
 * @returns The formatted date string.
 */
export const formatDate = (dateString: string): string =>
{
    const date = new Date(dateString);
    return date.toLocaleDateString('en-GB');
};

/**
 * Formats a date string to 'dd/mm/yyyy, HH:MM:SS' format.
 * @param dateString - The date string to format.
 * @returns The formatted date and time string.
 */
export const formatDateTime = (dateString: string): string =>
{
    const date = new Date(dateString);
    return date.toLocaleString('en-GB', {hour12: false});
};

/**
 * Formats a date string to 'dd<ordinal> Mon yyyy' format.
 * @param dateString - The date string to format.
 * @returns The formatted date string with ordinal suffix.
 */
export const formatDateWithOrdinal = (dateString: string): string =>
{
    const date = new Date(dateString);
    const day = date.getDate();
    const ordinalSuffix = getOrdinalSuffix(day);
    const options: Intl.DateTimeFormatOptions = {
        day: 'numeric',
        month: 'short',
        year: 'numeric'
    };
    return date.toLocaleDateString('en-GB', options).replace(day.toString(), `${day}${ordinalSuffix}`);
};

/**
 * Formats a date string to 'dd<ordinal> Mon yyyy @ HH:MM:SS' format.
 * @param dateString - The date string to format.
 * @returns The formatted date and time string with ordinal suffix.
 */
export const formatDateTimeWithOrdinal = (dateString: string): string =>
{
    const date = new Date(dateString);
    const day = date.getDate();
    const ordinalSuffix = getOrdinalSuffix(day);
    const options: Intl.DateTimeFormatOptions = {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false
    };
    return date.toLocaleString('en-GB', options).replace(',', ` @`).replace(day.toString(), `${day}${ordinalSuffix}`);
};