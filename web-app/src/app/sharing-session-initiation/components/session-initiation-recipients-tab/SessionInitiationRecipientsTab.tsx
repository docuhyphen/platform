import React, {useEffect, useRef} from 'react';
import {Field, Radio, RadioGroup} from "@fluentui/react-components";
import {useSessionInitiationRecipientsTabStyles} from "./SessionInitiationRecipientsTabStyles.tsx";
import {AppUserDetailedDto, OrganizationBasicDto} from "../../../models/models.tsx";
import {OrganizationGroupBasicDto} from "../../../../services/organizationApi";
import MyOrganizationRecipients from "./my-organization-recipients/MyOrganizationRecipients";
import ExternalOrganizationRecipients from "./external-organization-recipients/ExternalOrganizationRecipients";
import {SharingSessionNewMainRecipient} from "./new-recipient/NewRecipient";
import PeopleRecipients from "./people-recipients/PeopleRecipients";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {useIsMobile} from "../../../../utils/useMediaQuery.ts";

export enum SharingSessionInitiationRecipientMode
{
    PEOPLE = "PEOPLE",
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
    const {appUserPersonOrganization} = useAuth()
    const isMobile = useIsMobile();

    type RecipientModeSnapshot = {
        recipientOrg?: OrganizationBasicDto;
        recipientOrgUser?: AppUserDetailedDto;
        recipientOrgGroup?: OrganizationGroupBasicDto;
        internalParticipants?: AppUserDetailedDto[];
        newRecipient?: SharingSessionNewMainRecipient;
    };

    const modeSnapshotRef = useRef<Record<SharingSessionInitiationRecipientMode, RecipientModeSnapshot>>({
        [SharingSessionInitiationRecipientMode.PEOPLE]: {},
        [SharingSessionInitiationRecipientMode.MY_ORG]: {},
        [SharingSessionInitiationRecipientMode.EXTERNAL_ORG]: {},
        [SharingSessionInitiationRecipientMode.EMAIL]: {},
    });

    const saveCurrentModeSnapshot = (mode: SharingSessionInitiationRecipientMode) =>
    {
        modeSnapshotRef.current[mode] = {
            recipientOrg: props.recipientOrg,
            recipientOrgUser: props.recipientOrgUser,
            recipientOrgGroup: props.recipientOrgGroup,
            internalParticipants: props.internalParticipants,
            newRecipient: props.newRecipient,
        };
    };

    const restoreModeSnapshot = (mode: SharingSessionInitiationRecipientMode) =>
    {
        const snapshot = modeSnapshotRef.current[mode];
        props.setRecipientOrg(snapshot.recipientOrg);
        props.setRecipientOrgUser(snapshot.recipientOrgUser);
        props.setRecipientOrgGroup(snapshot.recipientOrgGroup);
        props.setInternalParticipants(snapshot.internalParticipants);
        if (snapshot.newRecipient)
        {
            props.setNewRecipient(snapshot.newRecipient);
        }
    };

    const onRecipientModeChange = (
        _: React.FormEvent<HTMLDivElement>,
        data: { value: SharingSessionInitiationRecipientMode }) =>
    {
        const nextMode = data.value as SharingSessionInitiationRecipientMode;
        if (nextMode === props.recipientMode)
        {
            return;
        }

        saveCurrentModeSnapshot(props.recipientMode);
        props.setRecipientMode(nextMode);
        restoreModeSnapshot(nextMode);
    }

    useEffect(() =>
    {
        saveCurrentModeSnapshot(props.recipientMode);
    }, [
        props.recipientMode,
        props.recipientOrg,
        props.recipientOrgUser,
        props.recipientOrgGroup,
        props.internalParticipants,
        props.newRecipient,
    ]);

    useEffect(() =>
    {
        console.log("SessionInitiationRecipientsTab useEffect triggered");
        if (!appUserPersonOrganization)
        {
            console.log("appUserPersonOrganization is undefined");
            if (props.recipientMode)
            {
                console.log("Setting recipient mode to PEOPLE due to undefined appUserPersonOrganization");
                props.setRecipientMode(SharingSessionInitiationRecipientMode.PEOPLE);
            }
        }

    }, [appUserPersonOrganization, props]);

    return (
        <div className={styles.recipientsTabContent}>
            {appUserPersonOrganization && <>
                <Field>
                    <RadioGroup
                        layout={isMobile ? "vertical" : "horizontal"}
                        value={props.recipientMode}
                        onChange={onRecipientModeChange}>
                        <Radio value={SharingSessionInitiationRecipientMode.PEOPLE}
                               label="People"/>
                        <Radio value={SharingSessionInitiationRecipientMode.MY_ORG} label="My Organization"/>
                        <Radio value={SharingSessionInitiationRecipientMode.EXTERNAL_ORG}
                               label="External Organization"/>
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

            {props.recipientMode === SharingSessionInitiationRecipientMode.PEOPLE && (
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
        </div>
    );
};

export default SessionInitiationRecipientsTab;