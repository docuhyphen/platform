import {describe, expect, it} from 'vitest';
import {
    toCanonicalDateTime,
    toDateTimeInputValue,
} from './fieldDateTimeCanonical';

/** The offset text the browser running these tests would attach to the given local reading. */
const localOffsetTextFor = (localReading: string): string =>
{
    const minutes = -new Date(localReading).getTimezoneOffset();

    if (minutes === 0) return 'Z';

    const sign = minutes < 0 ? '-' : '+';
    const absolute = Math.abs(minutes);
    const hours = String(Math.floor(absolute / 60)).padStart(2, '0');
    const remainder = String(absolute % 60).padStart(2, '0');

    return `${sign}${hours}:${remainder}`;
};

describe('toCanonicalDateTime', () =>
{
    it('attaches the browser offset to a local reading so the moment is unambiguous', () =>
    {
        const expected = `2026-08-31T10:15:00${localOffsetTextFor('2026-08-31T10:15:00')}`;

        expect(toCanonicalDateTime('2026-08-31T10:15')).toBe(expected);
    });

    it('keeps seconds a responder entered', () =>
    {
        const expected = `2026-08-31T10:15:30${localOffsetTextFor('2026-08-31T10:15:30')}`;

        expect(toCanonicalDateTime('2026-08-31T10:15:30')).toBe(expected);
    });

    it('leaves a reading that already names its offset alone', () =>
    {
        expect(toCanonicalDateTime('2026-08-31T10:15:30+02:00')).toBe('2026-08-31T10:15:30+02:00');
        expect(toCanonicalDateTime('2026-08-31T08:15:30Z')).toBe('2026-08-31T08:15:30Z');
    });

    it('reads a blank entry as no answer', () =>
    {
        expect(toCanonicalDateTime('')).toBeNull();
        expect(toCanonicalDateTime('   ')).toBeNull();
    });

    it('reads an unusable entry as no answer', () =>
    {
        expect(toCanonicalDateTime('31-08-2026 10:15')).toBeNull();
    });
});

describe('toDateTimeInputValue', () =>
{
    it('shows a stored moment as the local reading the picker expects', () =>
    {
        const canonical = '2026-08-31T08:15:30Z';
        const local = new Date(canonical);
        const expected = [
            String(local.getFullYear()).padStart(4, '0'),
            '-',
            String(local.getMonth() + 1).padStart(2, '0'),
            '-',
            String(local.getDate()).padStart(2, '0'),
            'T',
            String(local.getHours()).padStart(2, '0'),
            ':',
            String(local.getMinutes()).padStart(2, '0'),
        ].join('');

        expect(toDateTimeInputValue(canonical)).toBe(expected);
    });

    it('round-trips a local reading through the canonical form', () =>
    {
        const canonical = toCanonicalDateTime('2026-08-31T10:15');

        expect(toDateTimeInputValue(canonical)).toBe('2026-08-31T10:15');
    });

    it('shows nothing for an unanswered field', () =>
    {
        expect(toDateTimeInputValue(null)).toBe('');
        expect(toDateTimeInputValue(undefined)).toBe('');
        expect(toDateTimeInputValue('')).toBe('');
    });
});
