/** @vitest-environment jsdom */
import {afterEach, describe, expect, it} from 'vitest';
import {csrfHeaderName, readCsrfToken, requiresCsrfHeader} from './csrfToken';

const setCookie = (value: string) =>
{
    Object.defineProperty(document, 'cookie', {
        value,
        writable: true,
        configurable: true,
    });
};

describe('csrfToken', () =>
{
    afterEach(() =>
    {
        setCookie('');
    });

    it('reads the csrf token from a single cookie', () =>
    {
        setCookie('csrf_token=abc123');

        expect(readCsrfToken()).toBe('abc123');
    });

    it('reads the csrf token when other cookies are present', () =>
    {
        setCookie('theme=dark; csrf_token=abc123; locale=en');

        expect(readCsrfToken()).toBe('abc123');
    });

    it('does not confuse a cookie whose name merely ends with the csrf name', () =>
    {
        setCookie('not_csrf_token=wrong-value');

        expect(readCsrfToken()).toBeNull();
    });

    it('decodes an encoded token value', () =>
    {
        setCookie('csrf_token=a%2Bb%3Dc');

        expect(readCsrfToken()).toBe('a+b=c');
    });

    it('returns null when no csrf cookie is set', () =>
    {
        setCookie('theme=dark');

        expect(readCsrfToken()).toBeNull();
    });

    it('requires the header on state-changing methods only', () =>
    {
        expect(requiresCsrfHeader('post')).toBe(true);
        expect(requiresCsrfHeader('POST')).toBe(true);
        expect(requiresCsrfHeader('delete')).toBe(true);
        expect(requiresCsrfHeader('patch')).toBe(true);
        expect(requiresCsrfHeader('put')).toBe(true);
        expect(requiresCsrfHeader('get')).toBe(false);
        expect(requiresCsrfHeader('HEAD')).toBe(false);
        expect(requiresCsrfHeader(undefined)).toBe(false);
    });

    it('uses the header name the backend validates', () =>
    {
        expect(csrfHeaderName).toBe('X-CSRF-Token');
    });
});


