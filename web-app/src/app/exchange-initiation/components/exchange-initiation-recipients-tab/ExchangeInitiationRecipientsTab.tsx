import React, {useEffect, useRef} from 'react';
import {Badge, Dropdown, Field, InfoLabel, Option, Radio, RadioGroup, Text} from "@fluentui/react-components";
import {useExchangeInitiationRecipientsTabStyles} from "./ExchangeInitiationRecipientsTabStyles.tsx";
import {AppUserPublicDto, Capability, OrganizationBasicDto} from "../../../models/models.tsx";
import {OrganizationGroupBasicDto} from "../../../../services/organizationApi";
import {ExternalIdentityResolution} from "../../../../services/organizationTrust.ts";
import MyOrganizationRecipients from "./my-organization-recipients/MyOrganizationRecipients";
import TrustedOrganizationRecipients from "./trusted-organization-recipients/TrustedOrganizationRecipients.tsx";
import {ExchangeNewMainRecipient} from "./new-recipient/NewRecipient";
import PeopleRecipients from "./people-recipients/PeopleRecipients";
import MyGroupsRecipients from "./my-groups-recipients/MyGroupsRecipients";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {useIsMobile} from "../../../../utils/useMediaQuery.ts";
import {
    ASSIGNABLE_ROLES,
    CONSTRAINED_ROLES,
    ExchangeShareRoleName,
    ExchangeShareRoleDisplayNames,
} from '../../../../services/types/roles.ts';
import {ShareConstraints} from '../../../../services/types/dtos.ts';
import ShareConstraintToggles from '../../../components/share-constraints/ShareConstraintToggles.tsx';

export enum ExchangeInitiationRecipientMode
{
    PEOPLE = "PEOPLE",
    MY_GROUPS = "MY_GROUPS",
    MY_ORG = "MY_ORG",
    TRUSTED_ORG = "TRUSTED_ORG",
    EMAIL = "EMAIL"
}

interface ExchangeRecipientsTabProps
{
    recipientMode: ExchangeInitiationRecipientMode;
    setRecipientMode: (mode: ExchangeInitiationRecipientMode) => void;
    recipientOrg: OrganizationBasicDto | undefined;
    setRecipientOrg: (org: OrganizationBasicDto | undefined) => void;
    recipientOrgUser: AppUserPublicDto | undefined;
    setRecipientOrgUser: (user: AppUserPublicDto | undefined) => void;
    recipientOrgGroup: OrganizationGroupBasicDto | undefined;
    setRecipientOrgGroup: (group: OrganizationGroupBasicDto | undefined) => void;
    setRecipientResolution: (resolution?: ExternalIdentityResolution) => void;
    internalParticipants: AppUserPublicDto[];
    setInternalParticipants: (appUser: AppUserPublicDto[] | undefined) => void;
    newRecipient: ExchangeNewMainRecipient | undefined;
    setNewRecipient: (recipient: ExchangeNewMainRecipient | undefined) => void;
    isRequestingDocuments: boolean | null | undefined;
    recipientRole: ExchangeShareRoleName | undefined;
    setRecipientRole: (role: ExchangeShareRoleName | undefined) => void;
    recipientConstraints: ShareConstraints;
    setRecipientConstraints: (c: ShareConstraints) => void;
}

const ExchangeInitiationRecipientsTab: React.FC<ExchangeRecipientsTabProps> = (props) =>
{
    const styles = useExchangeInitiationRecipientsTabStyles();
    const {appUserPersonOrganization, hasCapability} = useAuth()
    const isMobile = useIsMobile();

    type RecipientModeSnapshot = {
        recipientOrg?: OrganizationBasicDto;
        recipientOrgUser?: AppUserPublicDto;
        recipientOrgGroup?: OrganizationGroupBasicDto;
        internalParticipants?: AppUserPublicDto[];
        newRecipient?: ExchangeNewMainRecipient;
    };

    const modeSnapshotRef = useRef<Record<ExchangeInitiationRecipientMode, RecipientModeSnapshot>>({
        [ExchangeInitiationRecipientMode.PEOPLE]: {},
        [ExchangeInitiationRecipientMode.MY_GROUPS]: {},
        [ExchangeInitiationRecipientMode.MY_ORG]: {},
        [ExchangeInitiationRecipientMode.TRUSTED_ORG]: {},
        [ExchangeInitiationRecipientMode.EMAIL]: {},
    });

    const saveCurrentModeSnapshot = (mode: ExchangeInitiationRecipientMode) =>
    {
        modeSnapshotRef.current[mode] = {
            recipientOrg: props.recipientOrg,
            recipientOrgUser: props.recipientOrgUser,
            recipientOrgGroup: props.recipientOrgGroup,
            internalParticipants: props.internalParticipants,
            newRecipient: props.newRecipient,
        };
    };

    const restoreModeSnapshot = (mode: ExchangeInitiationRecipientMode) =>
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
        data: { value: ExchangeInitiationRecipientMode }) =>
    {
        const nextMode = data.value as ExchangeInitiationRecipientMode;
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
            <Field>
                <RadioGroup
                    id={"exchange-recipient-mode-group"}
                    layout={isMobile ? "vertical" : "horizontal"}
                    value={props.recipientMode}
                    onChange={onRecipientModeChange}>
                    <Radio value={ExchangeInitiationRecipientMode.PEOPLE} label="People"/>
                    <Radio value={ExchangeInitiationRecipientMode.MY_GROUPS} label="My Groups"/>
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

            {(props.recipientMode === ExchangeInitiationRecipientMode.PEOPLE
                || props.recipientMode === ExchangeInitiationRecipientMode.EMAIL)
                && !props.recipientOrgUser
                && props.newRecipient?.email
                && props.newRecipient.email.includes('@')
                && appUserPersonOrganization != null
                && appUserPersonOrganization.verificationComplete
                && appUserPersonOrganization.isActive && (
                <div className={styles.externalBadgeRow}>
                    <Badge appearance="outline" color="warning">External recipient</Badge>
                </div>
            )}

            <div className={styles.roleSection}>
                <Field
                    label={
                        <InfoLabel
                            info={
                                <div>
                                    <Text size={200}>Each role determines what the recipient can do in this exchange.</Text>
                                    <div>
                                        <div><Text size={200} weight="semibold">Auto</Text> <Text size={200}>exch- derived from document permissions (Editor if write access granted, Viewer otherwise).</Text></div>
                                        <div><Text size={200} weight="semibold">Editor</Text> <Text size={200}>exch- can add, update, upload, and manage documents.</Text></div>
                                        <div><Text size={200} weight="semibold">Viewer</Text> <Text size={200}>exch- read-only access. Supports download and watermark constraints.</Text></div>
                                        <div><Text size={200} weight="semibold">Participant</Text> <Text size={200}>exch- flexible read access with optional constraints.</Text></div>
                                        <div><Text size={200} weight="semibold">Commenter</Text> <Text size={200}>exch- can view documents and leave comments.</Text></div>
                                        <div><Text size={200} weight="semibold">Reviewer</Text> <Text size={200}>exch- can view and comment, typically for approval workflows.</Text></div>
                                        <div><Text size={200} weight="semibold">Signer</Text> <Text size={200}>exch- read access plus formal signing capabilities.</Text></div>
                                    </div>
                                </div>
                            }
                        >
                            Recipient role
                        </InfoLabel>
                    }
                    hint="Defaults to Editor/Viewer based on document permissions."
                >
                    <Dropdown
                        id={"exchange-recipient-role-dropdown"}
                        size="small"
                        value={props.recipientRole ? ExchangeShareRoleDisplayNames[props.recipientRole] : 'Auto'}
                        selectedOptions={props.recipientRole ? [props.recipientRole] : ['AUTO']}
                        onOptionSelect={(_e, data) =>
                        {
                            const v = data.optionValue;
                            if (!v || v === 'AUTO')
                            {
                                props.setRecipientRole(undefined);
                                return;
                            }
                            props.setRecipientRole(v as ExchangeShareRoleName);
                        }}
                    >
                        <Option value="AUTO" text="Auto">Auto (derive from document permissions)</Option>
                        {[...ASSIGNABLE_ROLES].map((r) => (
                            <Option key={r} value={r} text={ExchangeShareRoleDisplayNames[r]}>
                                {ExchangeShareRoleDisplayNames[r]}
                            </Option>
                        ))}
                    </Dropdown>
                </Field>

                {props.recipientRole && CONSTRAINED_ROLES.has(props.recipientRole) && (
                    <div className={styles.constraintsRow}>
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

export default ExchangeInitiationRecipientsTab;
