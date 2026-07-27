import React from "react";
import {MessageBar, MessageBarBody, Spinner} from "@fluentui/react-components";
import {ExchangeRecipientInvitationDto} from "../../../../models/models.tsx";
import {
    decideExchangeRecipientInvitation,
    fetchPendingExchangeRecipientInvitations,
} from "../../../../../services/exchangeApi.ts";
import {realtimeService} from "../../../../../services/NotificationService.tsx";
import TrustedParticipantInvitationCard from "./TrustedParticipantInvitationCard.tsx";
import {useTrustedParticipantInvitationsStyles} from "./TrustedParticipantInvitationsStyles.tsx";

interface TrustedParticipantInvitationsProps
{
    onCountChange: (count: number) => void;
}

const TrustedParticipantInvitations: React.FC<TrustedParticipantInvitationsProps> = ({onCountChange}) =>
{
    const styles = useTrustedParticipantInvitationsStyles();
    const [invitations, setInvitations] = React.useState<ExchangeRecipientInvitationDto[]>([]);
    const [loading, setLoading] = React.useState(true);
    const [pendingId, setPendingId] = React.useState<string | null>(null);
    const [error, setError] = React.useState<string | null>(null);
    const [refreshVersion, setRefreshVersion] = React.useState(0);

    React.useEffect(() =>
        realtimeService.on("EXCHANGE_LIST_CHANGED", () =>
        {
            setRefreshVersion(version => version + 1);
        }), []);

    React.useEffect(() =>
    {
        let current = true;
        void fetchPendingExchangeRecipientInvitations()
            .then(items =>
            {
                if (!current) return;
                setInvitations(items);
                onCountChange(items.length);
            })
            .catch(() =>
            {
                if (current) setError("Trusted participant invitations could not be loaded.");
            })
            .finally(() =>
            {
                if (current) setLoading(false);
            });
        return () =>
        {
            current = false;
        };
    }, [onCountChange, refreshVersion]);

    const decide = async (invitation: ExchangeRecipientInvitationDto, decision: "ACCEPT" | "REJECT") =>
    {
        setPendingId(invitation.id);
        setError(null);
        try
        {
            await decideExchangeRecipientInvitation(invitation.id, decision);
            setInvitations(current =>
            {
                const remaining = current.filter(item => item.id !== invitation.id);
                onCountChange(remaining.length);
                return remaining;
            });
        }
        catch
        {
            setError(
                decision === "ACCEPT"
                    ? "This trusted participant invitation can no longer be accepted."
                    : "The trusted participant invitation could not be declined.",
            );
        }
        finally
        {
            setPendingId(null);
        }
    };

    if (loading)
    {
        return (
            <Spinner
                id={"trusted-participant-invitations-loading"}
                size={"small"}
            />
        );
    }
    if (invitations.length === 0 && !error) return null;

    return (
        <section
            id={"trusted-participant-invitations"}
            className={styles.container}
            aria-label={"Trusted participant invitations"}
        >
            {error && (
                <MessageBar
                    id={"trusted-participant-invitations-error"}
                    intent={"error"}
                >
                    <MessageBarBody id={"trusted-participant-invitations-error-message"}>
                        {error}
                    </MessageBarBody>
                </MessageBar>
            )}
            {invitations.map(invitation => (
                <TrustedParticipantInvitationCard
                    key={invitation.id}
                    invitation={invitation}
                    pending={pendingId !== null}
                    cardClassName={styles.card}
                    actionsClassName={styles.actions}
                    onDecision={(item, decision) => void decide(item, decision)}
                />
            ))}
        </section>
    );
};

export default TrustedParticipantInvitations;
