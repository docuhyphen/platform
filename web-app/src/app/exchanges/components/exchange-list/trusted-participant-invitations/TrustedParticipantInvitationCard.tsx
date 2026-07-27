import {Button, Card, Text} from "@fluentui/react-components";
import {ExchangeRecipientInvitationDto} from "../../../../models/models.tsx";

interface TrustedParticipantInvitationCardProps
{
    invitation: ExchangeRecipientInvitationDto;
    pending: boolean;
    cardClassName: string;
    actionsClassName: string;
    onDecision: (invitation: ExchangeRecipientInvitationDto, decision: "ACCEPT" | "REJECT") => void;
}

const TrustedParticipantInvitationCard = ({
    invitation,
    pending,
    cardClassName,
    actionsClassName,
    onDecision,
}: TrustedParticipantInvitationCardProps) => (
    <Card
        id={`trusted-participant-invitation-${invitation.id}`}
        className={cardClassName}
    >
        <Text
            id={`trusted-participant-invitation-${invitation.id}-title`}
            weight={"semibold"}
        >
            Trusted participant invitation
        </Text>
        <Text
            id={`trusted-participant-invitation-${invitation.id}-type`}
            size={200}
        >
            {invitation.selectionType === "TRUSTED_PERSON"
                ? "Membership verified person"
                : "Published Trusted Organization group"}
        </Text>
        <Text
            id={`trusted-participant-invitation-${invitation.id}-exchange`}
            size={200}
        >
            Exchange reference: {invitation.exchangeId}
        </Text>
        <div
            id={`trusted-participant-invitation-${invitation.id}-actions`}
            className={actionsClassName}
        >
            <Button
                id={`trusted-participant-accept-${invitation.id}`}
                appearance={"primary"}
                shape={"circular"}
                disabled={pending}
                onClick={() => onDecision(invitation, "ACCEPT")}
            >
                Accept access
            </Button>
            <Button
                id={`trusted-participant-reject-${invitation.id}`}
                appearance={"secondary"}
                shape={"circular"}
                disabled={pending}
                onClick={() => onDecision(invitation, "REJECT")}
            >
                Decline
            </Button>
        </div>
    </Card>
);

export default TrustedParticipantInvitationCard;
