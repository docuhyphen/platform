import React from "react";
import {Text} from "@fluentui/react-components";
import {OrganizationBasicDto} from "../../../../models/models.tsx";
import {OrganizationGroupBasicDto} from "../../../../../services/organizationApi.ts";
import {ExternalIdentityResolution} from "../../../../../services/organizationTrust.ts";
import TrustedGroupSelector from "./TrustedGroupSelector.tsx";
import TrustedOrganizationSelector from "./TrustedOrganizationSelector.tsx";
import TrustedPersonConfirmationCard from "./TrustedPersonConfirmationCard.tsx";
import TrustedPersonEmailResolver from "./TrustedPersonEmailResolver.tsx";
import TrustedRecipientMethodSelector from "./TrustedRecipientMethodSelector.tsx";
import TrustedVerificationState from "./TrustedVerificationState.tsx";
import {useTrustedOrganizationRecipients} from "./useTrustedOrganizationRecipients.ts";
import {useTrustedOrganizationRecipientsStyles} from "./TrustedOrganizationRecipientsStyles.tsx";

interface TrustedOrganizationRecipientsProps
{
    recipientOrg?: OrganizationBasicDto;
    recipientOrgGroup?: OrganizationGroupBasicDto;
    setRecipientOrg: (organization?: OrganizationBasicDto) => void;
    setRecipientOrgUser: (user: undefined) => void;
    setRecipientOrgGroup: (group?: OrganizationGroupBasicDto) => void;
    setRecipientResolution: (resolution?: ExternalIdentityResolution) => void;
}

const TrustedOrganizationRecipients: React.FC<TrustedOrganizationRecipientsProps> = (props) =>
{
    const styles = useTrustedOrganizationRecipientsStyles();
    const state = useTrustedOrganizationRecipients(props);
    return <div
        id={"trusted-organization-recipients"}
        className={styles.container}
    >
        <TrustedOrganizationSelector
            relationships={state.relationships}
            selectedRelationshipId={state.selectedRelationshipId}
            loading={state.loadingRelationships}
            onSelect={state.selectRelationship}
        />
        {props.recipientOrg && <TrustedRecipientMethodSelector
            method={state.method}
            canResolvePerson={state.canResolvePerson}
            canDiscoverGroups={state.canDiscoverGroups}
            onChange={state.selectMethod}
        />}
        {state.method === "PERSON" && props.recipientOrg && <>
            <TrustedPersonEmailResolver
                email={state.email}
                loading={state.resolvingPerson}
                error={state.error}
                onEmailChange={state.changeEmail}
                onResolve={state.resolvePerson}
            />
            {state.resolution && <>
                <TrustedPersonConfirmationCard resolution={state.resolution}/>
                <TrustedVerificationState
                    organizationName={state.resolution.organizationName}
                    expired={state.resolutionExpired}
                />
            </>}
        </>}
        {state.method === "GROUP" && <TrustedGroupSelector
            groups={state.groups}
            selectedGroupId={props.recipientOrgGroup?.id}
            organizationSelected={Boolean(props.recipientOrg?.id)}
            loading={state.loadingGroups}
            error={state.error}
            onSelect={state.selectGroup}
        />}
        {state.method === "GROUP" && props.recipientOrg && props.recipientOrgGroup && <Text
            id={"trusted-group-verification-label"}
            className={styles.verification}
        >
            Published by {props.recipientOrg.name}
        </Text>}
    </div>;
};

export default TrustedOrganizationRecipients;
