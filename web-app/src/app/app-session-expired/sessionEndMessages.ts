const DEFAULT_SESSION_END_MESSAGE = "Your session has expired. Please sign in again.";

const SESSION_END_MESSAGES: Record<string, string> = {
    SECURITY_SIGN_OUT: "Your session was ended for security reasons. Please sign in again.",
    ACCOUNT_DEPROVISIONED: "Your account has been deprovisioned. Contact your administrator for help.",
    ORG_MEMBERSHIP_INACTIVE: "Your organization membership is no longer active.",
    EXCHANGE_VERSION_MISMATCH: "Your session was invalidated because you signed out from all devices.",
    REFRESH_REUSE_DETECTED: "A suspicious sign-in attempt was detected and your session was terminated for your protection.",
    INACTIVITY_TIMEOUT: "You were signed out because your session was inactive.",
    SESSION_EXPIRED: "You were signed out because your session reached its maximum duration.",
    EXCHANGE_EXPIRED: "You were signed out because your session reached its maximum duration.",
    SECURITY_POLICY: "Your session was ended by a security policy. Please sign in again.",
    RISK_SIGNAL_DETECTED: "Your session was ended because its security context changed. Please sign in again.",
    DEPROVISIONED: "Your account has been deprovisioned. Contact your administrator for help.",
    ORG_INACTIVE: "Your organization is no longer active.",
    MEMBERSHIP_INACTIVE: "Your organization membership is no longer active.",
    PASSWORD_CHANGED: "You were signed out because your password changed.",
    REFRESH_INVALID: "Your session is no longer active. Please sign in again.",
    ADMIN_REVOKE: "Your session was ended by an administrator. Please sign in again if you still need access.",
    LOGOUT_ALL_DEVICES: "You were signed out because all device sessions were ended.",
    SYSTEM_MAINTENANCE: "Your session was ended for system maintenance. Please sign in again.",
};

export function getSessionEndMessage(reason: string | null): string
{
    const normalizedReason = reason?.toUpperCase() ?? "";
    return SESSION_END_MESSAGES[normalizedReason] ?? DEFAULT_SESSION_END_MESSAGE;
}
