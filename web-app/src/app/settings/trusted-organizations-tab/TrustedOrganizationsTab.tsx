import {Button, MessageBar, MessageBarBody, Spinner, Text, Title2} from "@fluentui/react-components";
import {useEffect, useState} from "react";
import {useAuth} from "../../../context/AuthContext.tsx";
import {Capability} from "../../models/models.tsx";
import {OrganizationTrustRelationship} from "../../../services/organizationTrust.ts";
import TrustActionDialog from "./action-dialog/TrustActionDialog.tsx";
import TrustedRelationshipDetail from "./relationship-detail/TrustedRelationshipDetail.tsx";
import TrustedRelationshipList from "./relationship-list/TrustedRelationshipList.tsx";
import TrustedOrganizationRequestDialog from "./request-dialog/TrustedOrganizationRequestDialog.tsx";
import {useTrustedOrganizationsTabStyles} from "./TrustedOrganizationsTabStyles.tsx";
import {TrustRelationshipAction, useTrustedOrganizations} from "./useTrustedOrganizations.ts";

interface PendingTrustAction
{
    action: TrustRelationshipAction;
    relationship: OrganizationTrustRelationship;
}

const TrustedOrganizationsTab = () =>
{
    const styles = useTrustedOrganizationsTabStyles();
    const {currentSession, hasCapability} = useAuth();
    const trust = useTrustedOrganizations();
    const [requestOpen, setRequestOpen] = useState(false);
    // The pending action is bound to the exact relationship and version captured when the dialog
    // opened, so a background notification refresh or selection change cannot redirect a confirmed
    // action to a different relationship or a newer version.
    const [pending, setPending] = useState<PendingTrustAction | null>(null);
    useEffect(() =>
    {
        setRequestOpen(false);
        setPending(null);
    }, [currentSession?.activeOrganizationId]);
    const openAction = (action: TrustRelationshipAction, relationship: OrganizationTrustRelationship) =>
        setPending({action, relationship});
    const confirmAction = async (reason?: string) =>
    {
        if (!pending) return;
        const current = trust.relationships.find(item => item.id === pending.relationship.id);
        if (!current || current.version !== pending.relationship.version)
        {
            setPending(null);
            trust.setError("This relationship changed before the action was confirmed. Review the latest state and try again.");
            return;
        }
        try
        {
            await trust.act(pending.action, pending.relationship, reason);
            setPending(null);
        }
        catch
        {
            return;
        }
    };
    const requestTrust = async (organizationId: string, message?: string) =>
    {
        try
        {
            await trust.requestTrust(organizationId, message);
            setRequestOpen(false);
        }
        catch
        {
            return;
        }
    };
    if (!hasCapability(Capability.ORG_TRUST_READ))
    {
        return (
            <div
                id={"trusted-organizations-not-authorized"}
                className={styles.empty}
            >
                <Text id={"trusted-organizations-not-authorized-message"}>You do not have access to Trusted Organizations.</Text>
            </div>
        );
    }
    return (
        <section
            id={"trusted-organizations-tab"}
            className={styles.root}
        >
            <header
                id={"trusted-organizations-header"}
                className={styles.header}
            >
                <div id={"trusted-organizations-heading"}>
                    <Title2 id={"trusted-organizations-title"}>Trusted Organizations</Title2>
                    <Text
                        id={"trusted-organizations-description"}
                        block
                    >
                        Jointly control verified organization collaboration and directional policy.
                    </Text>
                </div>
                {hasCapability(Capability.ORG_TRUST_REQUEST) && (
                    <Button
                        id={"trusted-organizations-new-request"}
                        shape={"circular"}
                        appearance={"primary"}
                        onClick={() => setRequestOpen(true)}
                    >
                        New request
                    </Button>
                )}
            </header>
            {trust.error && (
                <MessageBar
                    id={"trusted-organizations-error"}
                    intent={"error"}
                    className={styles.error}
                >
                    <MessageBarBody id={"trusted-organizations-error-message"}>{trust.error}</MessageBarBody>
                </MessageBar>
            )}
            {trust.loading ? (
                <Spinner
                    id={"trusted-organizations-loading"}
                    label={"Loading Trusted Organizations"}
                    size={"small"}
                />
            ) : trust.relationships.length === 0 ? (
                <div
                    id={"trusted-organizations-empty"}
                    className={styles.empty}
                >
                    <Text id={"trusted-organizations-empty-message"}>No trust relationships or requests yet.</Text>
                </div>
            ) : (
                <div
                    id={"trusted-organizations-content"}
                    className={styles.content}
                >
                    <TrustedRelationshipList
                        relationships={trust.relationships}
                        selectedId={trust.selected?.id}
                        onSelect={trust.setSelectedId}
                    />
                    {trust.selected && (
                        <TrustedRelationshipDetail
                            relationship={trust.selected}
                            policies={trust.policies}
                            busy={trust.busy}
                            hasCapability={hasCapability}
                            onAction={openAction}
                            onSavePolicy={trust.savePolicy}
                        />
                    )}
                </div>
            )}
            <TrustedOrganizationRequestDialog
                open={requestOpen}
                busy={trust.busy}
                onDismiss={() => setRequestOpen(false)}
                onSubmit={requestTrust}
            />
            <TrustActionDialog
                action={pending?.action ?? null}
                partnerName={pending?.relationship.partnerOrganizationName}
                busy={trust.busy}
                onDismiss={() => setPending(null)}
                onConfirm={confirmAction}
            />
        </section>
    );
};
export default TrustedOrganizationsTab;
