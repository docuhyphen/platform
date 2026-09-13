import {describe, expect, it} from 'vitest';
import {FieldValueType} from '../../../models/models';
import {formatFieldValue, isFieldValueEmpty, toCanonicalValue} from './fieldValueUtils';

describe('toCanonicalValue', () =>
{
    it('keeps a yes or no field on three readings', () =>
    {
        expect(toCanonicalValue(FieldValueType.BOOLEAN, true)).toBe(true);
        expect(toCanonicalValue(FieldValueType.BOOLEAN, false)).toBe(false);
        expect(toCanonicalValue(FieldValueType.BOOLEAN, undefined)).toBeNull();
        expect(toCanonicalValue(FieldValueType.BOOLEAN, null)).toBeNull();
        expect(toCanonicalValue(FieldValueType.BOOLEAN, '')).toBeNull();
    });

    it('sends numbers as exact digits rather than a converted number', () =>
    {
        const wide = '1234567890123456789012345678.0123456789';

        expect(toCanonicalValue(FieldValueType.DECIMAL, wide)).toBe(wide);
        expect(toCanonicalValue(FieldValueType.DECIMAL, '1.50')).toBe('1.50');
        expect(toCanonicalValue(FieldValueType.INTEGER, '42')).toBe('42');
        expect(toCanonicalValue(FieldValueType.INTEGER, '')).toBeNull();
    });

    it('sends a date-time with the offset it was entered at', () =>
    {
        expect(toCanonicalValue(FieldValueType.DATE_TIME, '2026-08-31T08:15:30Z'))
            .toBe('2026-08-31T08:15:30Z');
        expect(toCanonicalValue(FieldValueType.DATE_TIME, '')).toBeNull();
        expect(toCanonicalValue(FieldValueType.DATE_TIME, '2026-08-31T10:15'))
            .toMatch(/^2026-08-31T10:15:00(Z|[+-]\d{2}:\d{2})$/);
    });

    it('leaves the other types as they were', () =>
    {
        expect(toCanonicalValue(FieldValueType.SHORT_TEXT, 'note')).toBe('note');
        expect(toCanonicalValue(FieldValueType.SHORT_TEXT, '')).toBeNull();
        expect(toCanonicalValue(FieldValueType.MULTI_SELECT, ['a'])).toEqual(['a']);
        expect(toCanonicalValue(FieldValueType.DATE, '2026-08-31')).toBe('2026-08-31');
    });
});

describe('isFieldValueEmpty', () =>
{
    it('treats a no as an answer and an absent reading as empty', () =>
    {
        expect(isFieldValueEmpty(false)).toBe(false);
        expect(isFieldValueEmpty(undefined)).toBe(true);
        expect(isFieldValueEmpty(null)).toBe(true);
    });
});

describe('formatFieldValue', () =>
{
    it('shows a number too wide for a double without losing a digit', () =>
    {
        expect(formatFieldValue(FieldValueType.DECIMAL, '1234567890123456789012345678.0123456789', []))
            .toBe('1,234,567,890,123,456,789,012,345,678.0123456789');
        expect(formatFieldValue(FieldValueType.DECIMAL, '1234.50', [])).toBe('1,234.50');
    });

    it('shows a yes or no answer and marks an absent one', () =>
    {
        expect(formatFieldValue(FieldValueType.BOOLEAN, true, [])).toBe('Yes');
        expect(formatFieldValue(FieldValueType.BOOLEAN, false, [])).toBe('No');
        expect(formatFieldValue(FieldValueType.BOOLEAN, null, [])).toBe('Not provided');
    });

    it('shows a stored moment in the reader own zone', () =>
    {
        const canonical = '2026-08-31T08:15:30Z';
        const expected = new Intl.DateTimeFormat('en-GB', {
            day: 'numeric',
            month: 'short',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
        }).format(new Date(canonical));

        expect(formatFieldValue(FieldValueType.DATE_TIME, canonical, [])).toBe(expected);
    });
});
