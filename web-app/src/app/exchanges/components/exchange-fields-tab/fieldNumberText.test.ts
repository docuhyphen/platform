import {describe, expect, it} from 'vitest';
import {formatExactNumber, toExactNumberText} from './fieldNumberText';

describe('toExactNumberText', () =>
{
    it('keeps every digit a responder typed', () =>
    {
        expect(toExactNumberText('1234567890123456789012345678.0123456789'))
            .toBe('1234567890123456789012345678.0123456789');
    });

    it('keeps trailing fraction zeros so a configured scale survives', () =>
    {
        expect(toExactNumberText('1.50')).toBe('1.50');
    });

    it('reads an empty entry as no answer', () =>
    {
        expect(toExactNumberText('')).toBeNull();
        expect(toExactNumberText('   ')).toBeNull();
        expect(toExactNumberText(null)).toBeNull();
        expect(toExactNumberText(undefined)).toBeNull();
    });

    it('tidies the forms a number input can produce', () =>
    {
        expect(toExactNumberText('  42  ')).toBe('42');
        expect(toExactNumberText('+42')).toBe('42');
        expect(toExactNumberText('42.')).toBe('42');
        expect(toExactNumberText('.5')).toBe('0.5');
        expect(toExactNumberText('-.5')).toBe('-0.5');
        expect(toExactNumberText('1e5')).toBe('1e5');
    });

    it('reads an entry that is not a number as no answer', () =>
    {
        expect(toExactNumberText('abc')).toBeNull();
        expect(toExactNumberText('1.2.3')).toBeNull();
        expect(toExactNumberText('--1')).toBeNull();
    });
});

describe('formatExactNumber', () =>
{
    it('groups a number too wide for a double without losing a digit', () =>
    {
        expect(formatExactNumber('1234567890123456789012345678.0123456789'))
            .toBe('1,234,567,890,123,456,789,012,345,678.0123456789');
    });

    it('groups small numbers and keeps the fraction as given', () =>
    {
        expect(formatExactNumber('1234.50')).toBe('1,234.50');
        expect(formatExactNumber('42')).toBe('42');
        expect(formatExactNumber('-9876543.21')).toBe('-9,876,543.21');
    });

    it('returns anything it cannot group unchanged', () =>
    {
        expect(formatExactNumber('1e5')).toBe('1e5');
    });
});
