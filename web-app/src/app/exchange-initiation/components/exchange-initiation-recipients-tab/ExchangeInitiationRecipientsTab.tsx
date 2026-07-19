import React, {useEffect} from 'react';
import {Field, Radio, RadioGroup} from "@fluentui/react-components";
import {useExchangeInitiationRecipientsTabStyles} from "./ExchangeInitiationRecipientsTabStyles.tsx";
import {Capability} from "../../../models/models.tsx";
import MyOrganizationRecipients from "./my-organization-recipients/MyOrganizationRecipients";
import TrustedOrganizationRecipients from "./trusted-organization-recipients/TrustedOrganizationRecipients.tsx";
import PeopleRecipients from "./people-recipients/PeopleRecipients";
import MyGroupsRecipients from "./my-groups-recipients/MyGroupsRecipients";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {useIsMobile} from "../../../../utils/useMediaQuery.ts";
import {useRecipientModeSnapshots} from "./useRecipientModeSnapshots.ts";
import RecipientRoleSelector from "./recipient-role-selector/RecipientRoleSelector.tsx";
import ExternalRecipientBadge from "./external-recipient-badge/ExternalRecipientBadge.tsx";
import {ExchangeInitiationRecipientMode} from "./exchangeInitiationRecipientMode.ts";
import {ExchangeRecipientsTabProps} from "./ExchangeInitiationRecipientsTab.types.ts";

const ExchangeInitiationRecipientsTab: React.FC<ExchangeRecipientsTabProps> = (props) =>
{
    const styles = useExchangeInitiationRecipientsTabStyles();
    const {appUserPersonOrganization, hasCapability} = useAuth()
    const isMobile = useIsMobile();

    const {onRecipientModeChange} = useRecipientModeSnapshots(props);

    useEffect(() =>
    {
        if (!appUserPersonOrganization ||
            (!hasCapability(Capability.EXTERNAL_GROUP_DISCOVER) &&
                !hasCapability(Capability.EXTERNAL_IDENTITY_RESOLVE)))
        {
            // Only reset org-specific modes; PEOPLE and MY_GROUPS are always available
            if (props.recipientMode === ExchangeInitiationRecipientMode.MY_ORG ||
                props.recipientMode === ExchangeInitiationRecipientMode.TRUSTED_ORG)
            {
                props.setRecipientMode(ExchangeInitiationRecipientMode.PEOPLE);
            }
        }

    }, [appUserPersonOrganization, hasCapability, props]);

    return (
        <div className={styles.recipientsTabContent}>
            <Field id={"exchange-recipient-mode-field"}>
                <RadioGroup
                    id={"exchange-recipient-mode-group"}
                    layout={isMobile ? "vertical" : "horizontal"}
                    value={props.recipientMode}
                    onChange={onRecipientModeChange}>
                    <Radio id={"exchange-recipient-mode-people"}
                           value={ExchangeInitiationRecipientMode.PEOPLE}
                           label="People"/>
                    <Radio id={"exchange-recipient-mode-my-groups"}
                           value={ExchangeInitiationRecipientMode.MY_GROUPS}
                           label="My Groups"/>
                    {appUserPersonOrganization?.verificationComplete && appUserPersonOrganization?.isActive && <>
                        <Radio
                            id={"exchange-recipient-mode-my-organization"}
                            value={ExchangeInitiationRecipientMode.MY_ORG}
                            label="My Organization"
                        />
                        {(hasCapability(Capability.EXTERNAL_GROUP_DISCOVER) ||
                            hasCapability(Capability.EXTERNAL_IDENTITY_RESOLVE)) && (
                            <Radio
                                id={"exchange-recipient-mode-trusted-organization"}
                                value={ExchangeInitiationRecipientMode.TRUSTED_ORG}
                                label="Trusted Organization"
                            />
                        )}
                    </>}
                </RadioGroup>
            </Field>

            {props.recipientMode === ExchangeInitiationRecipientMode.MY_GROUPS && (
                <MyGroupsRecipients
                    recipientOrgGroup={props.recipientOrgGroup}
                    setRecipientOrgGroup={props.setRecipientOrgGroup}
                />
            )}

            {props.recipientMode === ExchangeInitiationRecipientMode.MY_ORG && (
                <MyOrganizationRecipients
                    recipientOrgUser={props.recipientOrgUser}
                    recipientOrgGroup={props.recipientOrgGroup}
                    internalParticipants={props.internalParticipants}
                    setRecipientOrgUser={props.setRecipientOrgUser}
                    setRecipientOrgGroup={props.setRecipientOrgGroup}
                    setInternalParticipants={props.setInternalParticipants}
                />
            )}

            {props.recipientMode === ExchangeInitiationRecipientMode.TRUSTED_ORG && (
                <TrustedOrganizationRecipients
                    recipientOrg={props.recipientOrg}
                    recipientOrgGroup={props.recipientOrgGroup}
                    setRecipientOrg={props.setRecipientOrg}
                    setRecipientOrgUser={props.setRecipientOrgUser}
                    setRecipientOrgGroup={props.setRecipientOrgGroup}
                    setRecipientResolution={props.setRecipientResolution}
                />
            )}

            {props.recipientMode === ExchangeInitiationRecipientMode.PEOPLE && (
                <PeopleRecipients
                    isRequestingDocuments={props.isRequestingDocuments}
                    recipientOrgUser={props.recipientOrgUser}
                    setRecipientOrgUser={props.setRecipientOrgUser}
                    newRecipient={props.newRecipient}
                    setNewRecipient={props.setNewRecipient}
                    internalParticipants={props.internalParticipants}
                    setInternalParticipants={props.setInternalParticipants}
                />
            )}

            <ExternalRecipientBadge
                recipientMode={props.recipientMode}
                recipientOrgUser={props.recipientOrgUser}
                newRecipientEmail={props.newRecipient?.email}
            />

            <RecipientRoleSelector
                recipientRole={props.recipientRole}
                setRecipientRole={props.setRecipientRole}
                recipientConstraints={props.recipientConstraints}
                setRecipientConstraints={props.setRecipientConstraints}
            />
        </div>
    );
};

export default ExchangeInitiationRecipientsTab;
