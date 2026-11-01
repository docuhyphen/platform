import {Divider, Spinner, Text} from "@fluentui/react-components";
import SecuritySessionPolicySummary
    from "../../components/security-session-policy-summary/SecuritySessionPolicySummary.tsx";
import IdpSessionPolicyCard from "./idp-session-policy-card/IdpSessionPolicyCard.tsx";
import {useSecuritySessionPolicy} from "./useSecuritySessionPolicy.ts";
import {useSecuritySessionPolicySectionStyles} from "./SecuritySessionPolicySectionStyles.tsx";

interface SecuritySessionPolicySectionProps
{
    organizationId: string;
}

export const SecuritySessionPolicySection = ({organizationId}: SecuritySessionPolicySectionProps) =>
{
    const styles = useSecuritySessionPolicySectionStyles();
    const {loading, error, settings, drafts, updateDraft, onSave, onReset} = useSecuritySessionPolicy(organizationId);

    return (
        <>
            <Divider id={"security-session-policy-section-divider-1"}
                     alignContent="start"
                     appearance="brand"
                     className={styles.divider}>
                Security Session Policy
            </Divider>

            {loading && (
                <div id={"security-session-policy-section-div-1"}
                     className={styles.loadingWrapper}>
                    <Spinner id={"security-session-policy-section-spinner-1"}
                             size="tiny"
                             label="Loading policy"/>
                </div>
            )}

            {error && (
                <div id={"security-session-policy-section-div-2"}
                     className={styles.error}>{error}</div>
            )}

            {settings && !loading && (
                <>
                    <SecuritySessionPolicySummary
                        effective={settings.effective}
                        guardrails={settings.guardrails}/>

                    {settings.idpConfigs.length === 0 && (
                        <Text id={"security-session-policy-section-text-1"}
                              size={300}
                              className={styles.noIdpText}>
                            No Identity Provider configurations exist yet.
                        </Text>
                    )}

                    {settings.idpConfigs.map(row =>
                    {
                        const draft = drafts[row.configId];
                        if (!draft) return null;
                        return (
                            <IdpSessionPolicyCard key={row.configId}
                                                  row={row}
                                                  draft={draft}
                                                  onDraftChange={updateDraft}
                                                  onSave={onSave}
                                                  onReset={onReset}/>
                        );
                    })}
                </>
            )}
        </>
    );
};

export default SecuritySessionPolicySection;
