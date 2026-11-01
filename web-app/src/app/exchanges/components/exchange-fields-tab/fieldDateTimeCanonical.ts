
const EXPLICIT_OFFSET = /(Z|[+-]\d{2}:?\d{2}|[+-]\d{2})$/;
const LOCAL_READING = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?$/;
const MINUTES_PER_HOUR = 60;

const pad = (value: number, width = 2): string => String(value).padStart(width, '0');

const offsetTextFor = (moment: Date): string =>
{
    const minutes = -moment.getTimezoneOffset();

    if (minutes === 0) return 'Z';

    const absolute = Math.abs(minutes);

    return `${minutes < 0 ? '-' : '+'}${pad(Math.floor(absolute / MINUTES_PER_HOUR))}`
        + `:${pad(absolute % MINUTES_PER_HOUR)}`;
};

export const toCanonicalDateTime = (value: unknown): string | null =>
{
    if (value === null || value === undefined) return null;

    const raw = String(value).trim();

    if (raw === '') return null;
    if (EXPLICIT_OFFSET.test(raw)) return raw;
    if (!LOCAL_READING.test(raw)) return null;

    const withSeconds = /T\d{2}:\d{2}$/.test(raw) ? `${raw}:00` : raw;
    const moment = new Date(withSeconds);

    if (Number.isNaN(moment.getTime())) return null;

    return `${withSeconds}${offsetTextFor(moment)}`;
};

export const toDateTimeInputValue = (canonical: unknown): string =>
{
    if (canonical === null || canonical === undefined) return '';

    const raw = String(canonical).trim();

    if (raw === '') return '';
    if (!EXPLICIT_OFFSET.test(raw)) return raw.slice(0, 16);

    const moment = new Date(raw);

    if (Number.isNaN(moment.getTime())) return '';

    return `${pad(moment.getFullYear(), 4)}-${pad(moment.getMonth() + 1)}-${pad(moment.getDate())}`
        + `T${pad(moment.getHours())}:${pad(moment.getMinutes())}`;
};
