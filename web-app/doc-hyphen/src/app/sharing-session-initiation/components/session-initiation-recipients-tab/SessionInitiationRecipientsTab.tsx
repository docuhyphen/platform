import React from 'react';
import {Field, Radio, RadioGroup} from "@fluentui/react-components";
import {useSessionInitiationRecipientsTabStyles} from "./SessionInitiationRecipientsTabStyles.tsx";
import {AppUserBasicDto, AppUserDetailedDto, OrganizationBasicDto} from "../../../models/models.tsx";
import {OrganizationGroupBasicDto} from "../../../../services/organizationApi";
import MyOrganizationRecipients from "./my-organization-recipients/MyOrganizationRecipients";
import ExternalOrganizationRecipients from "./external-organization-recipients/ExternalOrganizationRecipients";
import NewRecipient, {SharingSessionNewMainRecipient} from "./new-recipient/NewRecipient";

export enum SharingSessionInitiationRecipientMode
{
    MY_ORG = "MY_ORG",
    EXTERNAL_ORG = "EXTERNAL_ORG",
    USE_EMAIL = "USE_EMAIL"
}

interface SessionRecipientsTabProps
{
    recipientMode: SharingSessionInitiationRecipientMode;
    setRecipientMode: (mode: SharingSessionInitiationRecipientMode) => void;
    recipientOrg: OrganizationBasicDto | undefined;
    setRecipientOrg: (org: OrganizationBasicDto | undefined) => void;
    recipientOrgUser: AppUserDetailedDto | undefined;
    setRecipientOrgUser: (user: AppUserDetailedDto | undefined) => void;
    recipientOrgGroup: OrganizationGroupBasicDto | undefined;
    setRecipientOrgGroup: (group: OrganizationGroupBasicDto | undefined) => void;
    internalRecipients: AppUserDetailedDto[];
    setInternalRecipients: (appUser: AppUserDetailedDto[] | undefined) => void;
    newRecipient: SharingSessionNewMainRecipient | undefined;
    setNewRecipient: (recipient: SharingSessionNewMainRecipient | undefined) => void;
    isRequestingDocuments: boolean | null | undefined;
}

const SessionInitiationRecipientsTab: React.FC<SessionRecipientsTabProps> = (props) =>
{
    const styles = useSessionInitiationRecipientsTabStyles();

    const onRecipientModeChange = (
        _: React.FormEvent<HTMLDivElement>,
        data: { value: SharingSessionInitiationRecipientMode }) =>
    {
        props.setRecipientMode(data.value as SharingSessionInitiationRecipientMode);

        if (data.value !== SharingSessionInitiationRecipientMode.EXTERNAL_ORG)
        {
            props.setRecipientOrg(undefined);
        }
        if (data.value !== SharingSessionInitiationRecipientMode.MY_ORG)
        {
            props.setRecipientOrgUser(undefined);
            props.setRecipientOrgGroup(undefined);
        }
        if (data.value !== SharingSessionInitiationRecipientMode.USE_EMAIL)
        {
            props.setNewRecipient({
                email: '',
                firstName: '',
                lastName: ''
            });
        }
    }

    return (
        <div className={styles.recipientsTabContent}>
            <Field>
                <RadioGroup
                    layout={"horizontal"}
                    value={props.recipientMode}
                    onChange={onRecipientModeChange}>
                    <Radio value={SharingSessionInitiationRecipientMode.EXTERNAL_ORG} label="External Organization"/>
                    <Radio value={SharingSessionInitiationRecipientMode.MY_ORG} label="My Organization"/>
                    <Radio value={SharingSessionInitiationRecipientMode.USE_EMAIL} label="Use Email"/>
                </RadioGroup>
            </Field>

            {props.recipientMode === SharingSessionInitiationRecipientMode.MY_ORG && (
                <MyOrganizationRecipients
                    onSelectUser={props.setRecipientOrgUser}
                    onSelectGroup={props.setRecipientOrgGroup}
                />
            )}

            {props.recipientMode === SharingSessionInitiationRecipientMode.EXTERNAL_ORG && (
                <ExternalOrganizationRecipients
                    recipientOrg={props.recipientOrg}
                    recipientOrgUser={props.recipientOrgUser}
                    recipientOrgGroup={props.recipientOrgGroup}
                    internalRecipients={props.internalRecipients}
                    setRecipientOrg={props.setRecipientOrg}
                    setRecipientOrgUser={props.setRecipientOrgUser}
                    setRecipientOrgGroup={props.setRecipientOrgGroup}
                    setInternalRecipients={props.setInternalRecipients}
                />
            )}

            {props.recipientMode === SharingSessionInitiationRecipientMode.USE_EMAIL && (
                <NewRecipient
                    isRequestingDocuments={props.isRequestingDocuments}
                    onRecipientChange={props.setNewRecipient}
                />
            )}
        </div>
    );
};

export default SessionInitiationRecipientsTab;