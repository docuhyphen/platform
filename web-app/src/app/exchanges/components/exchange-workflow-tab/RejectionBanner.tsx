import { MessageBar, MessageBarBody, MessageBarTitle, Text } from "@fluentui/react-components";
import { WorkflowInstanceDetailDto } from "../../../models/models.tsx";
import { PRINCIPAL_KIND_LABELS } from "../../../settings/workflows-tab/workflowUtils.ts";

const formatEpoch = (ms: number) =>
    new Date(ms).toLocaleString(undefined, { dateStyle: "short", timeStyle: "short" });

interface Props
{
    instance: WorkflowInstanceDetailDto;
}

const RejectionBanner = ({ instance }: Props) =>
{
    if (instance.status !== "REJECTED") return null;

    const rejectedStep = instance.steps.find(s => s.status === "REJECTED");
    const rejectDecision = rejectedStep?.decisions.find(d => d.decision === "REJECT");

    return (
        <MessageBar intent="error">
            <MessageBarBody>
                <MessageBarTitle>Workflow Rejected</MessageBarTitle>
                {rejectDecision && (
                    <>
                        <Text size={200} block>
                            Rejected by:{" "}
                            {rejectDecision.displayName && rejectDecision.email
                                ? `${rejectDecision.displayName} (${rejectDecision.email})`
                                : rejectDecision.displayName || rejectDecision.email || rejectDecision.principalId}
                        </Text>
                        <Text size={200} block>
                            Date: {formatEpoch(rejectDecision.atEpochMillis)}
                        </Text>
                        {rejectDecision.reason && (
                            <Text size={200} block>
                                Reason: {rejectDecision.reason}
                            </Text>
                        )}
                    </>
                )}
            </MessageBarBody>
        </MessageBar>
    );
};

export default RejectionBanner;
