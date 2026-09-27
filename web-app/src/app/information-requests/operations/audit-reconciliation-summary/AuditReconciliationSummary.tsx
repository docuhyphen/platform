import {MessageBar, MessageBarBody, MessageBarTitle, Text} from "@fluentui/react-components";
import {InformationRequestAuditReconciliationDto} from "../../../models/models.tsx";
import {useAuditReconciliationSummaryStyles} from "./AuditReconciliationSummaryStyles.tsx";

interface AuditReconciliationSummaryProps
{
    reconciliation: InformationRequestAuditReconciliationDto;
}

const AuditReconciliationSummary = ({reconciliation}: AuditReconciliationSummaryProps) =>
{
    const styles = useAuditReconciliationSummaryStyles();

    if (reconciliation.reconciled)
    {
        return (
            <MessageBar id={"information-request-audit-reconciled"}
                        intent={"success"}>
                <MessageBarBody>
                    {`Every audited change has its audit record (${reconciliation.matchedTransitionCount} of ${reconciliation.auditedTransitionCount}).`}
                </MessageBarBody>
            </MessageBar>
        );
    }

    return (
        <MessageBar id={"information-request-audit-unreconciled"}
                    intent={"warning"}
                    layout={"multiline"}>
            <MessageBarBody>
                <MessageBarTitle>The audit history does not reconcile</MessageBarTitle>
                <div id={"information-request-audit-unreconciled-detail"}
                     className={styles.detail}>
                    {reconciliation.missing.length > 0 && (
                        <Text id={"information-request-audit-missing"}>
                            {`${reconciliation.missing.length} audited changes have no audit record, first at change ${reconciliation.missing[0].sequenceNumber}.`}
                        </Text>
                    )}
                    {reconciliation.unmatched.length > 0 && (
                        <Text id={"information-request-audit-unmatched"}>
                            {`${reconciliation.unmatched.length} audit records match no recorded change.`}
                        </Text>
                    )}
                    {reconciliation.unsealedEventCount > 0 && (
                        <Text id={"information-request-audit-unsealed"}>
                            {`${reconciliation.unsealedEventCount} audit records are not sealed yet.`}
                        </Text>
                    )}
                </div>
            </MessageBarBody>
        </MessageBar>
    );
};

export default AuditReconciliationSummary;
