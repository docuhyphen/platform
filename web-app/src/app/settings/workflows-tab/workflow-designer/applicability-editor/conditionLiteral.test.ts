import {describe, expect, it} from 'vitest';
import {FieldValueType} from '../../../../models/models';
import {toConditionLiteral, toConditionLiteralInputValue} from './conditionLiteral';

describe('toConditionLiteral', () =>
{
    it('keeps a numeric literal as exact digits so a wide answer can be matched', () =>
    {
        const wide = '1234567890123456789012345678.0123456789';

        expect(toConditionLiteral(FieldValueType.DECIMAL, wide)).toBe(wide);
        expect(toConditionLiteral(FieldValueType.INTEGER, '42')).toBe('42');
        expect(toConditionLiteral(FieldValueType.DECIMAL, '')).toBeUndefined();
    });

    it('names the moment a date-time literal means', () =>
    {
        expect(toConditionLiteral(FieldValueType.DATE_TIME, '2026-08-31T10:15'))
            .toMatch(/^2026-08-31T10:15:00(Z|[+-]\d{2}:\d{2})$/);
        expect(toConditionLiteral(FieldValueType.DATE_TIME, '2026-08-31T08:15:30Z'))
            .toBe('2026-08-31T08:15:30Z');
        expect(toConditionLiteral(FieldValueType.DATE_TIME, '')).toBeUndefined();
    });

    it('leaves the other types as the author typed them', () =>
    {
        expect(toConditionLiteral(FieldValueType.SHORT_TEXT, 'Pending')).toBe('Pending');
        expect(toConditionLiteral(FieldValueType.DATE, '2026-08-31')).toBe('2026-08-31');
    });
});

describe('toConditionLiteralInputValue', () =>
{
    it('shows a date-time literal as the local reading the picker expects', () =>
    {
        const canonical = '2026-08-31T08:15:30Z';
        const local = new Date(canonical);
        const expected = `${String(local.getFullYear()).padStart(4, '0')}`
            + `-${String(local.getMonth() + 1).padStart(2, '0')}`
            + `-${String(local.getDate()).padStart(2, '0')}`
            + `T${String(local.getHours()).padStart(2, '0')}`
            + `:${String(local.getMinutes()).padStart(2, '0')}`;

        expect(toConditionLiteralInputValue(FieldValueType.DATE_TIME, canonical)).toBe(expected);
    });

    it('shows the other literals as their own text', () =>
    {
        expect(toConditionLiteralInputValue(FieldValueType.DECIMAL, '1.50')).toBe('1.50');
        expect(toConditionLiteralInputValue(FieldValueType.SHORT_TEXT, 'Pending')).toBe('Pending');
        expect(toConditionLiteralInputValue(FieldValueType.DECIMAL, undefined)).toBe('');
        expect(toConditionLiteralInputValue(FieldValueType.DATE_TIME, undefined)).toBe('');
    });
});
