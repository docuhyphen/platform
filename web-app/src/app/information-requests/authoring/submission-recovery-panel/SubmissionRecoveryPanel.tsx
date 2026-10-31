import {useState} from "react";
import {MessageBar, MessageBarBody, Text, Title3} from "@fluentui/react-components";
import {InformationRequestSubmissionPackageDto} from "../../../models/models.tsx";
import InformationRequestLoadError from "../../shared/load-error/InformationRequestLoadError.tsx";
import SubmissionWithdrawalDialog from "../../submission/submission-withdrawal-dialog/SubmissionWithdrawalDialog.tsx";
import SubmissionRecoveryRow from "../submission-recovery-row/SubmissionRecoveryRow.tsx";
import {useSubmissionRecoveryPanelStyles} from "./SubmissionRecoveryPanelStyles.tsx";
import {useSubmissionRecovery} from "./useSubmissionRecovery.ts";

interface Props
{
    requestId: string;
    responseETag: string;
    onChanged: () => void;
}

const SubmissionRecoveryPanel = ({requestId, responseETag, onChanged}: Props) =>
{
    const styles = useSubmissionRecoveryPanelStyles();
    const recovery = useSubmissionRecovery(requestId, responseETag, onChanged);
    const [withdrawing, setWithdrawing] = useState<InformationRequestSubmissionPackageDto | null>(null);
    if (!recovery.loadError && recovery.packages.length === 0) return null;

    return (
        <section id={"information-request-submission-recovery"}
                 aria-labelledby={"information-request-submission-recovery-title"}
                 className={styles.panel}>
            <Title3 id={"information-request-submission-recovery-title"}
                    as={"h2"}>
                Submissions
            </Title3>
            <Text id={"information-request-submission-recovery-explanation"}
                  size={200}
                  className={styles.muted}>
                Withdrawing a submission for recovery opens its part again for the responding parties. The submission
                stays in the request history.
            </Text>
            {recovery.error && !withdrawing && (
                <MessageBar id={"information-request-submission-recovery-error"}
                            intent={"error"}
                            role={"alert"}>
                    <MessageBarBody id={"information-request-submission-recovery-error-body"}>{recovery.error}</MessageBarBody>
                </MessageBar>
            )}
            <Text id={"information-request-submission-recovery-status"}
                  role={"status"}
                  aria-live={"polite"}>
                {recovery.notice ?? ""}
            </Text>
            {recovery.loadError && (
                <InformationRequestLoadError id={"information-request-submission-recovery-load-error"}
                                             message={recovery.loadError}
                                             onRetry={() => void recovery.reload()}/>
            )}
            {recovery.packages.length > 0 && (
                <ul id={"information-request-submission-recovery-list"}
                    aria-labelledby={"information-request-submission-recovery-title"}
                    className={styles.list}>
                    {recovery.packages.map(submission => (
                        <SubmissionRecoveryRow key={submission.id}
                                               submission={submission}
                                               busy={recovery.busy}
                                               onWithdraw={() => setWithdrawing(submission)}/>
                    ))}
                </ul>
            )}
            {withdrawing && (
                <SubmissionWithdrawalDialog packageId={withdrawing.id}
                                            packageNumber={withdrawing.packageNumber}
                                            busy={recovery.busy}
                                            error={recovery.error}
                                            onWithdraw={recovery.withdraw}
                                            onDismiss={() => setWithdrawing(null)}/>
            )}
        </section>
    );
};

export default SubmissionRecoveryPanel;
