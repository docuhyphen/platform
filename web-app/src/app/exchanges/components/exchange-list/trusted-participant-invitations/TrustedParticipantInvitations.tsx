import React from "react";
import {Button, Card, MessageBar, MessageBarBody, Spinner, Text} from "@fluentui/react-components";
import {ExchangeRecipientInvitationDto} from "../../../../models/models.tsx";
import {
    decideExchangeRecipientInvitation,
    fetchPendingExchangeRecipientInvitations,
} from "../../../../../services/exchangeApi.ts";
import {realtimeService} from "../../../../../services/NotificationService.tsx";
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
            aria-label={"Trusted participant invitations"}>
            {error && (
                <MessageBar
                    id={"trusted-participant-invitations-error"}
                    intent={"error"}>
                    <MessageBarBody>{error}</MessageBarBody>
                </MessageBar>
            )}
            {invitations.map(invitation => (
                <Card
                    id={`trusted-participant-invitation-${invitation.id}`}
                    key={invitation.id}
                    className={styles.card}>
                    <Text weight={"semibold"}>Trusted participant invitation</Text>
                    <Text size={200}>
                        {invitation.selectionType === "TRUSTED_PERSON"
                            ? "Membership verified person"
                            : "Published Trusted Organization group"}
                    </Text>
                    <Text size={200}>Exchange reference: {invitation.exchangeId}</Text>
                    <div className={styles.actions}>
                        <Button
                            id={`trusted-participant-accept-${invitation.id}`}
                            appearance={"primary"}
                            shape={"circular"}
                            disabled={pendingId !== null}
                            onClick={() => void decide(invitation, "ACCEPT")}>
                            Accept access
                        </Button>
                        <Button
                            id={`trusted-participant-reject-${invitation.id}`}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={pendingId !== null}
                            onClick={() => void decide(invitation, "REJECT")}>
                            Decline
                        </Button>
                    </div>
                </Card>
            ))}
        </section>
    );
};

export default TrustedParticipantInvitations;
