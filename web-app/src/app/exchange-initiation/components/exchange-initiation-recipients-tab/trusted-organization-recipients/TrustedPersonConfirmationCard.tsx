import React from "react";
import {Card, Text} from "@fluentui/react-components";
import {ExternalIdentityResolution} from "../../../../../services/organizationTrust.ts";
import {useTrustedOrganizationRecipientsStyles} from "./TrustedOrganizationRecipientsStyles.tsx";

interface TrustedPersonConfirmationCardProps
{
    resolution: ExternalIdentityResolution;
}

const TrustedPersonConfirmationCard: React.FC<TrustedPersonConfirmationCardProps> = ({resolution}) =>
{
    const styles = useTrustedOrganizationRecipientsStyles();
    return <Card
        id={"trusted-person-confirmation-card"}
        className={styles.confirmationCard}
    >
        <Text
            id={"trusted-person-confirmation-name"}
            weight={"semibold"}
        >
            {resolution.displayName ?? "Verified member"}
        </Text>
        <Text id={"trusted-person-confirmation-email"}>{resolution.email}</Text>
        <Text id={"trusted-person-confirmation-organization"}>{resolution.organizationName}</Text>
        <Text id={"trusted-person-confirmation-expiry"}>
            Verification expires {new Date(resolution.expiresAt).toLocaleString()}
        </Text>
    </Card>;
};

export default TrustedPersonConfirmationCard;
