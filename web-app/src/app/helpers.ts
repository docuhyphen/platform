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

export const formatDate = (dateString: string): string =>
{
    const date = new Date(dateString);
    return date.toLocaleDateString('en-GB');
};

export const formatDateTime = (dateString: string): string =>
{
    const date = new Date(dateString);
    return date.toLocaleString('en-GB', {hour12: false});
};

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

export const formatDateTimeWithOrdinal = (dateString: string): string =>
{
    const date = new Date(dateString);
    const day = date.getDate();
    const ordinalSuffix = getOrdinalSuffix(day);
    const options: Intl.DateTimeFormatOptions = {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false
    };
    return date.toLocaleString('en-GB', options).replace(', ', ' @ ').replace(day.toString(), `${day}${ordinalSuffix}`);
};

export const formatAuditAction = (action: string): string =>
{
    return action
        .replace(/_/g, ' ')
        .toLowerCase()
        .replace(/\b\w/g, (char: string) => char.toUpperCase());
};