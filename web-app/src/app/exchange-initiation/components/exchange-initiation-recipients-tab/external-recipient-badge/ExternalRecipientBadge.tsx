import React from 'react';
import {Badge} from "@fluentui/react-components";
import {useExternalRecipientBadgeStyles} from "./ExternalRecipientBadgeStyles.tsx";
import {AppUserPublicDto} from "../../../../models/models.tsx";
import {useAuth} from "../../../../../context/AuthContext.tsx";
import {ExchangeInitiationRecipientMode} from "../exchangeInitiationRecipientMode.ts";

interface ExternalRecipientBadgeProps
{
    recipientMode: ExchangeInitiationRecipientMode;
    recipientOrgUser: AppUserPublicDto | undefined;
    newRecipientEmail: string | undefined;
}

const ExternalRecipientBadge: React.FC<ExternalRecipientBadgeProps> = (props) =>
{
    const styles = useExternalRecipientBadgeStyles();
    const {appUserPersonOrganization} = useAuth();

    const isEmailRecipientMode = props.recipientMode === ExchangeInitiationRecipientMode.PEOPLE ||
        props.recipientMode === ExchangeInitiationRecipientMode.EMAIL;

    const showBadge = isEmailRecipientMode &&
        !props.recipientOrgUser &&
        !!props.newRecipientEmail &&
        props.newRecipientEmail.includes('@') &&
        appUserPersonOrganization != null &&
        appUserPersonOrganization.verificationComplete &&
        appUserPersonOrganization.isActive;

    if (!showBadge)
    {
        return null;
    }

    return (
        <div className={styles.externalBadgeRow}>
            <Badge id={"exchange-recipient-external-badge"}
                   appearance="outline"
                   color="warning">External recipient</Badge>
        </div>
    );
};

export default ExternalRecipientBadge;
