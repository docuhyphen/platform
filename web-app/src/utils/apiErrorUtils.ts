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

const readableMessage = (value: unknown): string | undefined =>
    typeof value === 'string' && value.trim().length > 0 && !/^\s*</.test(value) ? value : undefined;

export const normalizeApiError = (error: unknown, fallbackMessage: string): NormalizedApiError =>
{
    if (typeof error === 'string')
    {
        return {message: readableMessage(error) ?? fallbackMessage};
    }

    if (typeof error === 'object' && error !== null)
    {
        const apiError = error as ApiErrorShape;
        return {
            message: readableMessage(apiError.errorMessage) ?? readableMessage(apiError.message) ?? fallbackMessage,
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
        case 'SIGN_UP_REQUIRED':
            return 'This email has not finished account setup yet. Sign up first to create your password and access the Exchange.';
        case 'STEP_UP_REQUIRED':
            return 'Please confirm your identity to continue with this action.';
        case 'MFA_EXCHANGE_EXPIRED':
            return 'Your verification session expired. Please start over and request a new code.';
        default:
            return error.message;
    }
};

export const getNoAuthOtpFriendlyMessage = getOtpFriendlyMessage;


