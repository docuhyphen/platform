import {Text} from "@fluentui/react-components";
import type {
    OrgAuthSessionPolicyEffective,
    OrgAuthSessionPolicyGuardrails,
} from "../../../../services/authApi.ts";
import {
    formatDurationRange,
    formatHoursAsDuration,
    formatMinutesAsDuration,
} from "./securitySessionDuration.ts";
import {useSecuritySessionPolicySummaryStyles} from "./SecuritySessionPolicySummaryStyles.tsx";

interface SecuritySessionPolicySummaryProps
{
    effective: OrgAuthSessionPolicyEffective;
    guardrails: OrgAuthSessionPolicyGuardrails;
}

interface PolicySummaryItem
{
    id: string;
    label: string;
    value: string;
    range: string;
    description: string;
}

const SecuritySessionPolicySummary = (
    {
        effective,
        guardrails,
    }: SecuritySessionPolicySummaryProps
) =>
{
    const styles = useSecuritySessionPolicySummaryStyles();
    const items: PolicySummaryItem[] = [
        {
            id: "access-token",
            label: "Access token lifetime",
            value: formatMinutesAsDuration(effective.accessTokenExpiryMinutes),
            range: formatDurationRange(
                formatMinutesAsDuration(guardrails.minAccessTokenExpiryMinutes),
                formatMinutesAsDuration(guardrails.maxAccessTokenExpiryMinutes),
            ),
            description: "Short-lived API access that renews automatically while the session remains valid.",
        },
        {
            id: "refresh-token",
            label: "Session renewal window",
            value: formatMinutesAsDuration(effective.refreshTokenExpiryMinutes),
            range: formatDurationRange(
                formatMinutesAsDuration(guardrails.minRefreshTokenExpiryMinutes),
                formatMinutesAsDuration(guardrails.maxRefreshTokenExpiryMinutes),
            ),
            description: "The sliding period in which an active session can renew its access token.",
        },
        {
            id: "max-session",
            label: "Maximum session lifetime",
            value: formatHoursAsDuration(effective.maxSessionDurationHours),
            range: formatDurationRange(
                formatHoursAsDuration(guardrails.minSessionMaxDurationHours),
                formatHoursAsDuration(guardrails.maxSessionMaxDurationHours),
            ),
            description: "The absolute limit after sign-in, even when the user remains active.",
        },
        {
            id: "idle-timeout",
            label: "Inactivity timeout",
            value: formatMinutesAsDuration(effective.idleTimeoutMinutes),
            range: formatDurationRange(
                formatMinutesAsDuration(guardrails.minIdleTimeoutMinutes),
                formatMinutesAsDuration(guardrails.maxIdleTimeoutMinutes),
            ),
            description: "The session ends when no user activity occurs for this duration.",
        },
    ];

    return (
        <section id={"security-session-policy-summary"}>
            <Text
                id={"security-session-policy-summary-introduction"}
                className={styles.introduction}
                size={200}>
                The strictest value across active Identity Provider configurations becomes the effective policy.
                Platform guardrails define the allowed range.
            </Text>
            <div
                id={"security-session-policy-summary-grid"}
                className={styles.grid}>
                {items.map(item => (
                    <div
                        id={`security-session-policy-${item.id}`}
                        className={styles.card}
                        key={item.id}>
                        <Text
                            id={`security-session-policy-${item.id}-label`}
                            weight={"semibold"}>
                            {item.label}
                        </Text>
                        <Text
                            id={`security-session-policy-${item.id}-value`}
                            className={styles.value}
                            size={500}
                            weight={"semibold"}>
                            {item.value}
                        </Text>
                        <Text
                            id={`security-session-policy-${item.id}-range`}
                            className={styles.supportingText}
                            size={200}>
                            Allowed range: {item.range}
                        </Text>
                        <Text
                            id={`security-session-policy-${item.id}-description`}
                            size={200}>
                            {item.description}
                        </Text>
                    </div>
                ))}
            </div>
        </section>
    );
};

export default SecuritySessionPolicySummary;
