import {Button, Text, Title3} from "@fluentui/react-components";
import {Capability} from "../../../models/models.tsx";
import {
    OrganizationTrustPolicies,
    OrganizationTrustPolicyUpdate,
    OrganizationTrustRelationship,
} from "../../../../services/organizationTrust.ts";
import {TrustRelationshipAction} from "../useTrustedOrganizations.ts";
import TrustPolicyEditor from "../policy-editor/TrustPolicyEditor.tsx";
import {useTrustedRelationshipDetailStyles} from "./TrustedRelationshipDetailStyles.tsx";

interface TrustedRelationshipDetailProps
{
    relationship: OrganizationTrustRelationship;
    policies: OrganizationTrustPolicies | null;
    busy: boolean;
    hasCapability: (capability: Capability) => boolean;
    onAction: (action: TrustRelationshipAction, relationship: OrganizationTrustRelationship) => void;
    onSavePolicy: (policyId: string, update: OrganizationTrustPolicyUpdate) => Promise<void>;
}

const formatDate = (timestamp?: number): string => timestamp
    ? new Intl.DateTimeFormat(undefined, {dateStyle: "medium", timeStyle: "short"}).format(timestamp)
    : "Not set";

const TrustedRelationshipDetail = ({
    relationship, policies, busy, hasCapability, onAction, onSavePolicy,
}: TrustedRelationshipDetailProps) =>
{
    const styles = useTrustedRelationshipDetailStyles();
    const requestCurrent = relationship.requestExpiresAt > Date.now();
    const incoming = relationship.status === "PENDING" && requestCurrent && !relationship.requestedByCurrentOrganization;
    const outgoing = relationship.status === "PENDING" && requestCurrent && relationship.requestedByCurrentOrganization;
    const active = relationship.status === "ACTIVE";
    const policyMutable = active || relationship.status === "PENDING" && requestCurrent;

    return (
        <section
            id={"trusted-organization-relationship-detail"}
            className={styles.root}
        >
            <div
                id={"trusted-organization-relationship-summary"}
                className={styles.summary}
            >
                <Title3 id={"trusted-organization-partner-name"}>{relationship.partnerOrganizationName}</Title3>
                <Text id={"trusted-organization-status"}>Status: {relationship.status}</Text>
                <Text id={"trusted-organization-requested-at"}>Requested: {formatDate(relationship.requestedAt)}</Text>
                {relationship.requestMessage && (
                    <Text id={"trusted-organization-request-message"}>Message: {relationship.requestMessage}</Text>
                )}
                {relationship.effectivelySuspended && (
                    <Text
                        id={"trusted-organization-suspension-state"}
                        className={styles.warning}
                    >
                        New trusted operations are suspended.
                    </Text>
                )}
            </div>
            <div
                id={"trusted-organization-actions"}
                className={styles.actions}
            >
                {incoming && hasCapability(Capability.ORG_TRUST_DECIDE) && (
                    <>
                        <Button
                            id={"trusted-organization-accept"}
                            shape={"circular"}
                            appearance={"primary"}
                            disabled={busy}
                            onClick={() => onAction("ACCEPT", relationship)}
                        >Accept</Button>
                        <Button
                            id={"trusted-organization-reject"}
                            shape={"circular"}
                            appearance={"secondary"}
                            disabled={busy}
                            onClick={() => onAction("REJECT", relationship)}
                        >Reject</Button>
                    </>
                )}
                {outgoing && hasCapability(Capability.ORG_TRUST_REQUEST) && (
                    <Button
                        id={"trusted-organization-withdraw"}
                        shape={"circular"}
                        appearance={"secondary"}
                        disabled={busy}
                        onClick={() => onAction("WITHDRAW", relationship)}
                    >Withdraw</Button>
                )}
                {active && hasCapability(Capability.ORG_TRUST_SUSPEND) && !relationship.suspendedByCurrentOrganization && (
                    <Button
                        id={"trusted-organization-suspend"}
                        shape={"circular"}
                        appearance={"secondary"}
                        disabled={busy}
                        onClick={() => onAction("SUSPEND", relationship)}
                    >Suspend</Button>
                )}
                {active && hasCapability(Capability.ORG_TRUST_SUSPEND) && relationship.suspendedByCurrentOrganization && (
                    <Button
                        id={"trusted-organization-resume"}
                        shape={"circular"}
                        appearance={"primary"}
                        disabled={busy}
                        onClick={() => onAction("RESUME", relationship)}
                    >Resume</Button>
                )}
                {active && hasCapability(Capability.ORG_TRUST_SUSPEND) && (
                    <Button
                        id={"trusted-organization-end"}
                        shape={"circular"}
                        appearance={"secondary"}
                        disabled={busy}
                        onClick={() => onAction("END", relationship)}
                    >End trust</Button>
                )}
            </div>
            {policies && (
                <div
                    id={"trusted-organization-policies"}
                    className={styles.policies}
                >
                    {policies.policies.map(policy => (
                        <TrustPolicyEditor
                            key={policy.id}
                            policy={policy}
                            canManage={policyMutable && policy.ownedByCurrentOrganization && hasCapability(Capability.ORG_TRUST_POLICY_MANAGE)}
                            busy={busy}
                            onSave={onSavePolicy}
                        />
                    ))}
                </div>
            )}
        </section>
    );
};

export default TrustedRelationshipDetail;
