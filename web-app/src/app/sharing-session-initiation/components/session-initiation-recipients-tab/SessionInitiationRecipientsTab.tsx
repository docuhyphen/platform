import React, {useEffect} from 'react';
import {Field, Radio, RadioGroup} from "@fluentui/react-components";
import {useSessionInitiationRecipientsTabStyles} from "./SessionInitiationRecipientsTabStyles.tsx";
import {AppUserDetailedDto, OrganizationBasicDto} from "../../../models/models.tsx";
import {OrganizationGroupBasicDto} from "../../../../services/organizationApi";
import MyOrganizationRecipients from "./my-organization-recipients/MyOrganizationRecipients";
import ExternalOrganizationRecipients from "./external-organization-recipients/ExternalOrganizationRecipients";
import NewRecipient, {SharingSessionNewMainRecipient} from "./new-recipient/NewRecipient";
import {useAuth} from "../../../../context/AuthContext.tsx";

export enum SharingSessionInitiationRecipientMode
{
    MY_ORG = "MY_ORG",
    EXTERNAL_ORG = "EXTERNAL_ORG",
    EMAIL = "EMAIL"
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
    internalParticipants: AppUserDetailedDto[];
    setInternalParticipants: (appUser: AppUserDetailedDto[] | undefined) => void;
    newRecipient: SharingSessionNewMainRecipient | undefined;
    setNewRecipient: (recipient: SharingSessionNewMainRecipient | undefined) => void;
    isRequestingDocuments: boolean | null | undefined;
}

const SessionInitiationRecipientsTab: React.FC<SessionRecipientsTabProps> = (props) =>
{
    const styles = useSessionInitiationRecipientsTabStyles();
    const {appUser, appUserPersonOrganization} = useAuth()

    const onRecipientModeChange = (
        _: React.FormEvent<HTMLDivElement>,
        data: { value: SharingSessionInitiationRecipientMode }) =>
    {
        props.setRecipientMode(data.value as SharingSessionInitiationRecipientMode);
        props.setRecipientOrg(undefined);
        props.setRecipientOrgUser(undefined);
        props.setRecipientOrgGroup(undefined);

        if (data.value !== SharingSessionInitiationRecipientMode.EMAIL)
        {
            props.setNewRecipient({
                email: '',
                firstName: '',
                lastName: ''
            });
        }
    }

    useEffect(() =>
    {
        console.log("SessionInitiationRecipientsTab useEffect triggered");
        if (!appUserPersonOrganization)
        {
            console.log("appUserPersonOrganization is undefined");
            if (props.recipientMode)
            {
                console.log("Setting recipient mode to USE_EMAIL due to undefined appUserPersonOrganization");
                props.setRecipientMode(SharingSessionInitiationRecipientMode.EMAIL);
            }
        }

    }, [appUserPersonOrganization, props]);

    return (
        <div className={styles.recipientsTabContent}>
            {appUserPersonOrganization && <>
                <Field>
                    <RadioGroup
                        layout={"horizontal"}
                        value={props.recipientMode}
                        onChange={onRecipientModeChange}>
                        <Radio value={SharingSessionInitiationRecipientMode.EXTERNAL_ORG}
                               label="External Organization"/>
                        <Radio value={SharingSessionInitiationRecipientMode.MY_ORG} label="My Organization"/>
                        <Radio value={SharingSessionInitiationRecipientMode.EMAIL} label="Use Email"/>
                    </RadioGroup>
                </Field>
            </>
            }
            {props.recipientMode === SharingSessionInitiationRecipientMode.MY_ORG && (
                <MyOrganizationRecipients
                    recipientOrgUser={props.recipientOrgUser}
                    recipientOrgGroup={props.recipientOrgGroup}
                    internalParticipants={props.internalParticipants}
                    setRecipientOrgUser={props.setRecipientOrgUser}
                    setRecipientOrgGroup={props.setRecipientOrgGroup}
                    setInternalParticipants={props.setInternalParticipants}
                />
            )}

            {props.recipientMode === SharingSessionInitiationRecipientMode.EXTERNAL_ORG && (
                <ExternalOrganizationRecipients
                    recipientOrg={props.recipientOrg}
                    recipientOrgUser={props.recipientOrgUser}
                    recipientOrgGroup={props.recipientOrgGroup}
                    internalParticipants={props.internalParticipants}
                    setRecipientOrg={props.setRecipientOrg}
                    setRecipientOrgUser={props.setRecipientOrgUser}
                    setRecipientOrgGroup={props.setRecipientOrgGroup}
                    setInternalParticipants={props.setInternalParticipants}
                />
            )}

            {props.recipientMode === SharingSessionInitiationRecipientMode.EMAIL && (
                <NewRecipient
                    isRequestingDocuments={props.isRequestingDocuments}
                    setNewRecipient={props.setNewRecipient}
                    newRecipient={props.newRecipient}
                    internalParticipants={props.internalParticipants}
                    setInternalParticipants={props.setInternalParticipants}
                />
            )}
        </div>
    );
};

export default SessionInitiationRecipientsTab;