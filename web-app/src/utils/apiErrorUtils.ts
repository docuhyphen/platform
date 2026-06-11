export type ApiErrorShape = {
    message?: string;
    errorMessage?: string;
    reasonCode?: string;
    retryAfterSeconds?: number;
};

export type NormalizedApiError = {
    message: string;
    reasonCode?: string;
    retryAfterSeconds?: number;
};

export const normalizeApiError = (error: unknown, fallbackMessage: string): NormalizedApiError =>
{
    if (typeof error === 'string')
    {
        return {message: error};
    }

    if (typeof error === 'object' && error !== null)
    {
        const apiError = error as ApiErrorShape;
        return {
            message: apiError.errorMessage || apiError.message || fallbackMessage,
            reasonCode: apiError.reasonCode,
            retryAfterSeconds: apiError.retryAfterSeconds,
        };
    }

    return {message: fallbackMessage};
};

const formatDuration = (seconds: number): string =>
{
    const roundedSeconds = Math.max(1, Math.ceil(seconds));
    if (roundedSeconds < 60)
    {
        return `${roundedSeconds} seconds`;
    }

    const minutes = Math.floor(roundedSeconds / 60);
    const remainingSeconds = roundedSeconds % 60;
    if (remainingSeconds === 0)
    {
        return `${minutes} minute${minutes > 1 ? 's' : ''}`;
    }

    return `${minutes} minute${minutes > 1 ? 's' : ''} ${remainingSeconds} seconds`;
};

/**
 * App-wide friendly copy for OTP / verification-code flows.
 *
 * Covers reason codes from:
 *  - No-auth exchange OTP (`OTP_REQUIRED`, `OTP_INVALID`, `OTP_EXPIRED`,
 *    `OTP_NOT_ISSUED`, `OTP_RATE_LIMITED`, `OTP_LOCKED`)
 *  - Sharing session status transition errors (`INVALID_STATUS_TRANSITION`)
 *  - Sign-in / sign-up / account recovery flows when the backend chooses to
 *    emit reason codes (`PASSWORD_CHANGE_REQUIRED`, `TEMP_PASSWORD_EXPIRED`,
 *    `STEP_UP_REQUIRED`, `MFA_EXCHANGE_EXPIRED`).
 *
 * Unknown reason codes fall back to the original API message.
 */
export const getOtpFriendlyMessage = (error: NormalizedApiError): string =>
{
    switch (error.reasonCode)
    {
        case 'OTP_REQUIRED':
            return 'Enter the 6-digit verification code to continue.';
        case 'OTP_INVALID':
            return 'That verification code is not valid. Check the email and try again.';
        case 'OTP_EXPIRED':
            return 'That verification code has expired. Request a new code to continue.';
        case 'OTP_NOT_ISSUED':
            return 'No verification code has been issued yet. Request a code first.';
        case 'OTP_RATE_LIMITED':
            return `Please wait ${formatDuration(error.retryAfterSeconds ?? 30)} before requesting another code.`;
        case 'OTP_LOCKED':
            return `Too many invalid attempts. Try again in ${formatDuration(error.retryAfterSeconds ?? 300)}.`;
        case 'INVALID_STATUS_TRANSITION':
            return 'This sharing request is no longer awaiting your decision.';
        case 'PASSWORD_CHANGE_REQUIRED':
            return 'You must change your temporary password before signing in.';
        case 'TEMP_PASSWORD_EXPIRED':
            return 'Your temporary password has expired. Start account recovery to set a new one.';
        case 'STEP_UP_REQUIRED':
            return 'Please confirm your identity to continue with this action.';
        case 'MFA_EXCHANGE_EXPIRED':
            return 'Your verification session expired. Please start over and request a new code.';
        default:
            return error.message;
    }
};

/**
 * Backwards-compatible alias for no-auth exchange OTP error mapping.
 * New callers should use `getOtpFriendlyMessage` directly.
 */
export const getNoAuthOtpFriendlyMessage = getOtpFriendlyMessage;


