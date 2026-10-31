import {Badge, Button, Text} from "@fluentui/react-components";
import {InformationRequestSubmissionPackageDto} from "../../../models/models.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import {humanizedKey} from "../../submission/submissionLabels.ts";
import {useSubmissionRecoveryRowStyles} from "./SubmissionRecoveryRowStyles.tsx";

interface Props
{
    submission: InformationRequestSubmissionPackageDto;
    busy: boolean;
    onWithdraw: () => void;
}

const standingOf = (submission: InformationRequestSubmissionPackageDto): {label: string; color: "success" | "subtle"} =>
{
    if (submission.withdrawn) return {label: "Withdrawn", color: "subtle"};
    return submission.current ? {label: "Submitted", color: "success"} : {label: "Replaced", color: "subtle"};
};

const SubmissionRecoveryRow = ({submission, busy, onWithdraw}: Props) =>
{
    const styles = useSubmissionRecoveryRowStyles();
    const id = `information-request-submission-recovery-${submission.id}`;
    const standing = standingOf(submission);
    const title = submission.stageKey
        ? `Submission ${submission.packageNumber} - ${humanizedKey(submission.stageKey)}`
        : `Submission ${submission.packageNumber}`;

    return (
        <li id={id}
            className={styles.item}>
            <div id={`${id}-summary`}
                 className={styles.summary}>
                <Text id={`${id}-title`}
                      weight={"semibold"}>
                    {title}
                </Text>
                <Text id={`${id}-submitted-at`}
                      size={200}
                      className={styles.muted}>
                    {`Submitted ${formatInformationRequestTime(submission.submittedAt)}`}
                </Text>
                {submission.withdrawn && submission.withdrawnAt && (
                    <Text id={`${id}-withdrawn-at`}
                          size={200}
                          className={styles.muted}>
                        {`Withdrawn ${formatInformationRequestTime(submission.withdrawnAt)}`
                            + (submission.withdrawalReasonCode ? `: ${submission.withdrawalReasonCode}` : "")}
                    </Text>
                )}
            </div>
            <div id={`${id}-actions`}
                 className={styles.actions}>
                <Badge id={`${id}-state`}
                       appearance={"tint"}
                       color={standing.color}>
                    {standing.label}
                </Badge>
                {submission.withdrawable && (
                    <Button id={`${id}-withdraw`}
                            appearance={"secondary"}
                            shape={"circular"}
                            size={"small"}
                            disabled={busy}
                            aria-label={`Withdraw submission ${submission.packageNumber}`}
                            onClick={onWithdraw}>
                        Withdraw
                    </Button>
                )}
            </div>
        </li>
    );
};

export default SubmissionRecoveryRow;
