import {describe, expect, it} from 'vitest';
import {getOtpFriendlyMessage, normalizeApiError} from './apiErrorUtils';

describe('normalizeApiError', () =>
{
    it('uses errorMessage when provided', () =>
    {
        const normalized = normalizeApiError({errorMessage: 'Primary message', message: 'Fallback message'}, 'Default');
        expect(normalized.message).toBe('Primary message');
    });

    it('handles unknown primitive values', () =>
    {
        const normalized = normalizeApiError(42, 'Default error');
        expect(normalized.message).toBe('Default error');
    });
});

describe('getOtpFriendlyMessage', () =>
{
    it('maps OTP_REQUIRED to standard copy', () =>
    {
        const message = getOtpFriendlyMessage({message: 'Raw', reasonCode: 'OTP_REQUIRED'});
        expect(message).toBe('Enter the 6-digit verification code to continue.');
    });

    it('maps OTP_RATE_LIMITED with retry duration', () =>
    {
        const message = getOtpFriendlyMessage({message: 'Raw', reasonCode: 'OTP_RATE_LIMITED', retryAfterSeconds: 75});
        expect(message).toContain('1 minute 15 seconds');
    });

    it('maps OTP_LOCKED with retry duration', () =>
    {
        const message = getOtpFriendlyMessage({message: 'Raw', reasonCode: 'OTP_LOCKED', retryAfterSeconds: 120});
        expect(message).toContain('2 minutes');
    });

    it('falls back to API message for unknown reason codes', () =>
    {
        const message = getOtpFriendlyMessage({message: 'Server-provided message', reasonCode: 'SOMETHING_NEW'});
        expect(message).toBe('Server-provided message');
    });
});



