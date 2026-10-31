import {MessageBar, MessageBarBody, MessageBarTitle, Text} from "@fluentui/react-components";
import {InformationRequestAuditReconciliationDto} from "../../../models/models.tsx";
import {formatInformationRequestCount} from "../../shared/informationRequestFormatting.ts";
import {countedText} from "../operationsLabels.ts";
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
        const matched = formatInformationRequestCount(reconciliation.matchedTransitionCount);
        return (
            <MessageBar id={"information-request-audit-reconciled"}
                        intent={"success"}>
                <MessageBarBody id={"information-request-audit-reconciled-body"}>
                    {`Every audited change has its audit record (${matched} of ${formatInformationRequestCount(reconciliation.auditedTransitionCount)}).`}
                </MessageBarBody>
            </MessageBar>
        );
    }

    return (
        <MessageBar id={"information-request-audit-unreconciled"}
                    intent={"warning"}
                    layout={"multiline"}>
            <MessageBarBody id={"information-request-audit-unreconciled-body"}>
                <MessageBarTitle id={"information-request-audit-unreconciled-title"}>
                    The audit history does not reconcile
                </MessageBarTitle>
                <div id={"information-request-audit-unreconciled-detail"}
                     className={styles.detail}>
                    {reconciliation.missing.length > 0 && (
                        <Text id={"information-request-audit-missing"}>
                            {`${countedText(reconciliation.missing.length, "audited change has", "audited changes have")} no audit record, `
                                + `first at change ${reconciliation.missing[0].sequenceNumber}.`}
                        </Text>
                    )}
                    {reconciliation.unmatched.length > 0 && (
                        <Text id={"information-request-audit-unmatched"}>
                            {`${countedText(reconciliation.unmatched.length, "audit record matches", "audit records match")} no recorded change.`}
                        </Text>
                    )}
                    {reconciliation.unsealedEventCount > 0 && (
                        <Text id={"information-request-audit-unsealed"}>
                            {`${countedText(reconciliation.unsealedEventCount, "audit record is", "audit records are")} not sealed yet.`}
                        </Text>
                    )}
                </div>
            </MessageBarBody>
        </MessageBar>
    );
};

export default AuditReconciliationSummary;
