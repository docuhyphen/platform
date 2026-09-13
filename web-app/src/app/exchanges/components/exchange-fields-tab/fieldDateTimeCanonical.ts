/**
 * A date-time answer names one moment, so it is sent with the offset it was entered at.
 *
 * A `datetime-local` picker only knows a wall-clock reading. Sending that reading on its own leaves
 * the server to guess which moment was meant, so the browser offset for that reading is attached
 * before the answer leaves, and a stored moment is turned back into the local reading the picker
 * expects on the way in.
 */

const EXPLICIT_OFFSET = /(Z|[+-]\d{2}:?\d{2}|[+-]\d{2})$/;
const LOCAL_READING = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?$/;
const MINUTES_PER_HOUR = 60;

const pad = (value: number, width = 2): string => String(value).padStart(width, '0');

/** The offset text the browser uses for the given moment, accounting for daylight saving. */
const offsetTextFor = (moment: Date): string =>
{
    const minutes = -moment.getTimezoneOffset();

    if (minutes === 0) return 'Z';

    const absolute = Math.abs(minutes);

    return `${minutes < 0 ? '-' : '+'}${pad(Math.floor(absolute / MINUTES_PER_HOUR))}`
        + `:${pad(absolute % MINUTES_PER_HOUR)}`;
};

/** The canonical form of a date-time entry, or null when the field holds no answer. */
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

/** A stored moment as the local reading a `datetime-local` picker can display. */
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
