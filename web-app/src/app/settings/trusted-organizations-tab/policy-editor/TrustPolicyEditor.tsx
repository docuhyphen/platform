import {Button, Switch, Text} from "@fluentui/react-components";
import {useEffect, useState} from "react";
import {
    OrganizationTrustPolicy,
    OrganizationTrustPolicyUpdate,
} from "../../../../services/organizationTrust.ts";
import {useTrustPolicyEditorStyles} from "./TrustPolicyEditorStyles.tsx";

interface TrustPolicyEditorProps
{
    policy: OrganizationTrustPolicy;
    canManage: boolean;
    busy: boolean;
    onSave: (policyId: string, update: OrganizationTrustPolicyUpdate) => Promise<void>;
}

type BooleanPolicyField = "allowExchangesToPartner" | "allowExchangesFromPartner" |
    "allowPartnerMemberResolution" | "allowPartnerGroupDiscovery" | "shareMemberDisplayName";

const fields: Array<{key: BooleanPolicyField; label: string}> = [
    {key: "allowExchangesToPartner", label: "Allow Exchanges to partner"},
    {key: "allowExchangesFromPartner", label: "Allow Exchanges from partner"},
    {key: "allowPartnerMemberResolution", label: "Allow exact-email member resolution"},
    {key: "allowPartnerGroupDiscovery", label: "Allow published group discovery"},
    {key: "shareMemberDisplayName", label: "Share resolved member display names"},
];

const toUpdate = (policy: OrganizationTrustPolicy): OrganizationTrustPolicyUpdate => ({
    expectedRevision: policy.revision,
    allowExchangesToPartner: policy.allowExchangesToPartner,
    allowExchangesFromPartner: policy.allowExchangesFromPartner,
    allowPartnerMemberResolution: policy.allowPartnerMemberResolution,
    allowPartnerGroupDiscovery: policy.allowPartnerGroupDiscovery,
    shareMemberDisplayName: policy.shareMemberDisplayName,
    expiresAt: policy.expiresAt,
    reviewDueAt: policy.reviewDueAt,
});

const TrustPolicyEditor = ({policy, canManage, busy, onSave}: TrustPolicyEditorProps) =>
{
    const styles = useTrustPolicyEditorStyles();
    const [draft, setDraft] = useState<OrganizationTrustPolicyUpdate>(toUpdate(policy));

    useEffect(() =>
    {
        setDraft(toUpdate(policy));
    }, [policy]);

    return (
        <section
            id={`trusted-organization-policy-${policy.id}`}
            className={styles.root}
        >
            <Text
                id={`trusted-organization-policy-${policy.id}-owner`}
                weight={"semibold"}
            >
                {policy.ownedByCurrentOrganization ? "Your organization policy" : `${policy.policyOwnerOrganizationName} policy`}
            </Text>
            <div
                id={`trusted-organization-policy-${policy.id}-switches`}
                className={styles.switches}
            >
                {fields.map(field => (
                    <Switch
                        id={`trusted-organization-policy-${policy.id}-${field.key}`}
                        key={field.key}
                        label={field.label}
                        checked={draft[field.key]}
                        disabled={!canManage || busy}
                        onChange={(_, data) => setDraft(current => ({...current, [field.key]: data.checked}))}
                    />
                ))}
            </div>
            {canManage && (
                <div
                    id={`trusted-organization-policy-${policy.id}-actions`}
                    className={styles.actions}
                >
                    <Button
                        id={`trusted-organization-policy-${policy.id}-save`}
                        shape={"circular"}
                        appearance={"primary"}
                        disabled={busy}
                        onClick={() => void onSave(policy.id, draft).catch(() => undefined)}
                    >
                        Save policy
                    </Button>
                </div>
            )}
        </section>
    );
};

export default TrustPolicyEditor;
