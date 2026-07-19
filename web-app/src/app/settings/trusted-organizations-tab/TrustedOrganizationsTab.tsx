import {Button, MessageBar, MessageBarBody, Spinner, Text, Title2} from "@fluentui/react-components";
import {useAuth} from "../../../context/AuthContext.tsx";
import {Capability} from "../../models/models.tsx";
import TrustActionDialog from "./action-dialog/TrustActionDialog.tsx";
import TrustedRelationshipDetail from "./relationship-detail/TrustedRelationshipDetail.tsx";
import TrustedRelationshipList from "./relationship-list/TrustedRelationshipList.tsx";
import TrustedOrganizationRequestDialog from "./request-dialog/TrustedOrganizationRequestDialog.tsx";
import {useTrustedOrganizationsTabStyles} from "./TrustedOrganizationsTabStyles.tsx";
import {useTrustedOrganizations} from "./useTrustedOrganizations.ts";
import {useTrustedOrganizationDialogs} from "./useTrustedOrganizationDialogs.ts";

const TrustedOrganizationsTab = () =>
{
    const styles = useTrustedOrganizationsTabStyles();
    const {currentSession, hasCapability} = useAuth();
    const trust = useTrustedOrganizations();
    const dialogs = useTrustedOrganizationDialogs(currentSession?.activeOrganizationId, trust);
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
                        onClick={() => dialogs.setRequestOpen(true)}
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
                            onAction={dialogs.openAction}
                            onSavePolicy={trust.savePolicy}
                        />
                    )}
                </div>
            )}
            <TrustedOrganizationRequestDialog
                open={dialogs.requestOpen}
                busy={trust.busy}
                onDismiss={() => dialogs.setRequestOpen(false)}
                onSubmit={dialogs.requestTrust}
            />
            <TrustActionDialog
                action={dialogs.pending?.action ?? null}
                partnerName={dialogs.pending?.relationship.partnerOrganizationName}
                busy={trust.busy}
                onDismiss={() => dialogs.setPending(null)}
                onConfirm={dialogs.confirmAction}
            />
        </section>
    );
};
export default TrustedOrganizationsTab;
