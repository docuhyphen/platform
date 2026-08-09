/**
 * Maps the stable error codes the OAuth callback redirects with onto user-facing copy.
 *
 * The backend deliberately sends only a code. Rendering server-supplied text would both leak
 * account detail (such as which provider another account is linked to) and put an attacker
 * controlled string on the page.
 */
const OAUTH_ERROR_MESSAGES: Record<string, string> = {
    OAUTH_INVALID_REQUEST: 'That sign-in link was incomplete. Please try again.',
    OAUTH_INVALID_STATE: 'Your sign-in session expired or could not be verified. Please try again.',
    OAUTH_RATE_LIMITED: 'Too many sign-in attempts. Please wait a moment and try again.',
    OAUTH_PROVIDER_CONFLICT: 'This account already uses a different sign-in provider. Sign in with your existing method, then change it from your profile.',
    OAUTH_EMAIL_NOT_VERIFIED: 'Your identity provider did not confirm that you own this email address. Sign up with this email first, then link the provider from your profile.',
    OAUTH_LINK_CONTEXT_MISSING: 'We could not match this sign-in to your account. Please start again.',
    OAUTH_ACCOUNT_NOT_ELIGIBLE: 'This account cannot be used for sign-in. Please contact your administrator.',
    OAUTH_STEP_UP_MISMATCH: 'Re-authentication did not match your account. Please try again.',
    OAUTH_FAILED: 'Sign-in failed. Please try again.',
};

const GENERIC_OAUTH_ERROR = 'Sign-in failed. Please try again.';

/** Resolves an error code from the URL into copy, falling back to a neutral message. */
export const resolveOAuthErrorMessage = (errorCode: string | null | undefined): string | null =>
{
    if (!errorCode)
    {
        return null;
    }

    return OAUTH_ERROR_MESSAGES[errorCode.toUpperCase()] ?? GENERIC_OAUTH_ERROR;
};

