import { MessageBar, MessageBarBody, MessageBarTitle, Text } from "@fluentui/react-components";
import { WorkflowInstanceDetailDto } from "../../../models/models.tsx";

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
        <MessageBar id={"rejection-banner-message-bar-1"}
                    intent="error">
            <MessageBarBody id={"rejection-banner-message-bar-body-1"}>
                <MessageBarTitle id={"rejection-banner-message-bar-title-1"}>Workflow Rejected</MessageBarTitle>
                {rejectDecision && (
                    <>
                        <Text id={"rejection-banner-text-1"}
                              size={200}
                              block>
                            Rejected by:{" "}
                            {rejectDecision.displayName && rejectDecision.email
                                ? `${rejectDecision.displayName} (${rejectDecision.email})`
                                : rejectDecision.displayName || rejectDecision.email || rejectDecision.principalId}
                        </Text>
                        <Text id={"rejection-banner-text-2"}
                              size={200}
                              block>
                            Date: {formatEpoch(rejectDecision.atEpochMillis)}
                        </Text>
                        {rejectDecision.reason && (
                            <Text id={"rejection-banner-text-3"}
                                  size={200}
                                  block>
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
