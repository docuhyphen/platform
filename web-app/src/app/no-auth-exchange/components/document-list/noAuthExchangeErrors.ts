/**
 * Shape returned by the API's error responses. `reasonCode` lets the UI branch on a stable
 * identifier instead of matching on message text, which silently breaks when copy changes.
 */
export interface NoAuthExchangeApiError
{
    errorMessage?: string;
    message?: string;
    reasonCode?: string;
}

/** Reason code the API sends when the recipient has no live verified access window. */
export const NO_AUTH_ACCESS_EXPIRED_REASON = "NO_AUTH_ACCESS_EXPIRED";

const asApiError = (error: unknown): NoAuthExchangeApiError | null =>
{
    if (typeof error === "object" && error !== null)
    {
        return error as NoAuthExchangeApiError;
    }
    return null;
};

export const getErrorMessage = (error: unknown, fallback: string): string =>
{
    if (typeof error === "string")
    {
        return error;
    }
    const apiError = asApiError(error);
    return apiError?.errorMessage || apiError?.message || fallback;
};

export const isAccessVerificationError = (error: unknown): boolean =>
{
    const apiError = asApiError(error);
    if (apiError?.reasonCode === NO_AUTH_ACCESS_EXPIRED_REASON)
    {
        return true;
    }
    // Older responses predate the reason code and only carry the message.
    return /access verification has expired/i.test(getErrorMessage(error, ""));
};

export const requiresSignIn = (message: string): boolean =>
    /requires sign\s?in/i.test(message) || /requires recipient sign-?in/i.test(message);

/** Turns a raw API message into guidance the recipient can act on. */
export const toActionableUploadError = (message: string): string =>
{
    if (/Permission to upload document not granted/i.test(message))
    {
        return "You cannot upload to this document because uploads are disabled for this request. Ask the person who requested your documents to enable uploads in Manage Access, or send the file to them outside the app.";
    }
    if (/Sharing exchange has already ended/i.test(message) || /exchange has ended/i.test(message))
    {
        return "This request has ended, so uploads are no longer possible. Contact the person who requested your documents outside the app if you still need to send this file.";
    }
    if (requiresSignIn(message))
    {
        return "This request now requires sign in. Sign in to your account and reopen the request to continue uploading documents.";
    }
    if (/access verification has expired/i.test(message))
    {
        return "Your access has expired. Ask the person who requested your documents to resend an access code, then enter it to continue.";
    }
    if (/verification code/i.test(message))
    {
        return "That access code is invalid or expired. Ask the requester to resend a new code and try again.";
    }
    return message;
};

