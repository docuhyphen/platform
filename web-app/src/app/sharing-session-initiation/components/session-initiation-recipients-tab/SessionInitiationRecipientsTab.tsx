import React, {useEffect, useRef} from 'react';
import {Badge, Dropdown, Field, Option, Radio, RadioGroup, Text} from "@fluentui/react-components";
import {useSessionInitiationRecipientsTabStyles} from "./SessionInitiationRecipientsTabStyles.tsx";
import {AppUserDetailedDto, OrganizationBasicDto} from "../../../models/models.tsx";
import {OrganizationGroupBasicDto} from "../../../../services/organizationApi";
import MyOrganizationRecipients from "./my-organization-recipients/MyOrganizationRecipients";
import ExternalOrganizationRecipients from "./external-organization-recipients/ExternalOrganizationRecipients";
import {SharingSessionNewMainRecipient} from "./new-recipient/NewRecipient";
import PeopleRecipients from "./people-recipients/PeopleRecipients";
import MyGroupsRecipients from "./my-groups-recipients/MyGroupsRecipients";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {useIsMobile} from "../../../../utils/useMediaQuery.ts";
import {
    ASSIGNABLE_ROLES,
    CONSTRAINED_ROLES,
    SessionShareRole,
    SessionShareRoleDisplayNames,
} from '../../../../services/types/roles.ts';
import {ShareConstraints} from '../../../../services/types/dtos.ts';
import ShareConstraintToggles from '../../../components/share-constraints/ShareConstraintToggles.tsx';

export enum SharingSessionInitiationRecipientMode
{
    PEOPLE = "PEOPLE",
    MY_GROUPS = "MY_GROUPS",
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
    // Plan 07 G1 — recipient role + constraints override at initiation.
    recipientRole: SessionShareRole | undefined;
    setRecipientRole: (role: SessionShareRole | undefined) => void;
    recipientConstraints: ShareConstraints;
    setRecipientConstraints: (c: ShareConstraints) => void;
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
        [SharingSessionInitiationRecipientMode.MY_GROUPS]: {},
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
            // Only reset org-specific modes; PEOPLE and MY_GROUPS are always available
            if (props.recipientMode === SharingSessionInitiationRecipientMode.MY_ORG ||
                props.recipientMode === SharingSessionInitiationRecipientMode.EXTERNAL_ORG)
            {
                console.log("Setting recipient mode to PEOPLE due to undefined appUserPersonOrganization");
                props.setRecipientMode(SharingSessionInitiationRecipientMode.PEOPLE);
            }
        }

    }, [appUserPersonOrganization, props]);

    return (
        <div className={styles.recipientsTabContent}>
            <Field>
                <RadioGroup
                    layout={isMobile ? "vertical" : "horizontal"}
                    value={props.recipientMode}
                    onChange={onRecipientModeChange}>
                    <Radio value={SharingSessionInitiationRecipientMode.PEOPLE} label="People"/>
                    <Radio value={SharingSessionInitiationRecipientMode.MY_GROUPS} label="My Groups"/>
                    {appUserPersonOrganization?.verificationComplete && appUserPersonOrganization?.isActive && <>
                        <Radio value={SharingSessionInitiationRecipientMode.MY_ORG} label="My Organization"/>
                        <Radio value={SharingSessionInitiationRecipientMode.EXTERNAL_ORG}
                               label="External Organization"/>
                    </>}
                </RadioGroup>
            </Field>

            {props.recipientMode === SharingSessionInitiationRecipientMode.MY_GROUPS && (
                <MyGroupsRecipients
                    recipientOrgGroup={props.recipientOrgGroup}
                    setRecipientOrgGroup={props.setRecipientOrgGroup}
                />
            )}

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

            {/* Plan 07 G5 — soften the external-recipient case into a badge instead of a hard
                error. Backend permits this when OrganizationSettings.allowExternalCustomerSharing
                is true (default). Shown only in PEOPLE/EMAIL mode when the user typed a fresh
                email rather than selecting a known app user. */}
            {(props.recipientMode === SharingSessionInitiationRecipientMode.PEOPLE
                || props.recipientMode === SharingSessionInitiationRecipientMode.EMAIL)
                && !props.recipientOrgUser
                && props.newRecipient?.email
                && props.newRecipient.email.includes('@') && (
                <div style={{marginTop: 8, display: 'flex', alignItems: 'center', gap: 8}}>
                    <Badge appearance="outline" color="warning">External recipient</Badge>
                    <Text size={200}>
                        This recipient isn't in your organization — they'll receive a B2C-style
                        invite. Use the constraints below to limit what they can do.
                    </Text>
                </div>
            )}

            {/* Plan 07 G1 — recipient access controls. The dropdown overrides the legacy
                auto-derivation (EDITOR if any write flag, else VIEWER). When the chosen role
                supports constraints (PARTICIPANT / VIEWER), show the full toggle panel so the
                initiator can pin watermark / max-views / MFA / download / reshare up-front
                instead of having to PATCH the access entry after creation. */}
            <div style={{marginTop: 16, paddingTop: 12, borderTop: '1px solid var(--colorNeutralStroke2)'}}>
                <Field label="Recipient role" hint="Defaults to Editor/Viewer based on document permissions.">
                    <Dropdown
                        size="small"
                        value={props.recipientRole ? SessionShareRoleDisplayNames[props.recipientRole] : 'Auto'}
                        selectedOptions={props.recipientRole ? [props.recipientRole] : ['AUTO']}
                        onOptionSelect={(_e, data) =>
                        {
                            const v = data.optionValue;
                            if (!v || v === 'AUTO')
                            {
                                props.setRecipientRole(undefined);
                                return;
                            }
                            props.setRecipientRole(v as SessionShareRole);
                        }}
                    >
                        <Option value="AUTO" text="Auto">Auto (derive from document permissions)</Option>
                        {[...ASSIGNABLE_ROLES].map((r) => (
                            <Option key={r} value={r} text={SessionShareRoleDisplayNames[r]}>
                                {SessionShareRoleDisplayNames[r]}
                            </Option>
                        ))}
                    </Dropdown>
                </Field>

                {props.recipientRole && CONSTRAINED_ROLES.has(props.recipientRole) && (
                    <div style={{marginTop: 8}}>
                        <ShareConstraintToggles
                            constraints={props.recipientConstraints}
                            onChange={props.setRecipientConstraints}
                        />
                    </div>
                )}
            </div>
        </div>
    );
};

export default SessionInitiationRecipientsTab;