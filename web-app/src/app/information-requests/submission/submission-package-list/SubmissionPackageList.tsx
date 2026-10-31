import {useState} from "react";
import SubmissionWithdrawalDialog from "../submission-withdrawal-dialog/SubmissionWithdrawalDialog.tsx";
import {Badge, Button, Text} from "@fluentui/react-components";
import {InformationRequestSubmissionPackageDto} from "../../../models/models.tsx";
import {humanizedKey} from "../submissionLabels.ts";
import {useSubmissionPackageListStyles} from "./SubmissionPackageListStyles.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";

interface Props
{
    packages: InformationRequestSubmissionPackageDto[];
    busy: boolean;
    closed: boolean;
    error?: string | null;
    onWithdraw: (packageId: string, reasonCode?: string) => Promise<boolean> | void;
}

const answersCorrection = (
    submission: InformationRequestSubmissionPackageDto,
    packages: InformationRequestSubmissionPackageDto[],
): boolean => {
    const previous = packages.find(candidate => candidate.id === submission.previousPackageId);
    return previous !== undefined && !previous.withdrawn;
};

const packageState = (submission: InformationRequestSubmissionPackageDto): string => {
    if (submission.withdrawn) return "Withdrawn";
    return submission.current ? "Submitted" : "Replaced";
};

const SubmissionPackageList = ({packages, busy, closed, error, onWithdraw}: Props) =>
{
    const styles = useSubmissionPackageListStyles();
    const [withdrawing, setWithdrawing] = useState<InformationRequestSubmissionPackageDto | null>(null);

    if (packages.length === 0) return null;

    return (
        <section id={"information-request-submission-packages"}
                 className={styles.list}>
            <Text id={"information-request-submission-packages-title"}
                  weight={"semibold"}>
                Submissions
            </Text>
            {packages.map(submission => (
                <div id={`information-request-submission-package-${submission.id}`}
                     key={submission.id}
                     className={styles.row}>
                    <div id={`information-request-submission-package-${submission.id}-summary`}
                         className={styles.summary}>
                        <Text id={`information-request-submission-package-${submission.id}-title`}
                              weight={"semibold"}>
                            {`Submission ${submission.packageNumber}`}
                            {submission.stageKey && ` - ${humanizedKey(submission.stageKey)}`}
                        </Text>
                        <Text id={`information-request-submission-package-${submission.id}-submitted-at`}
                              className={styles.detail}>
                            {formatInformationRequestTime(submission.submittedAt)}
                        </Text>
                    </div>
                    <Badge id={`information-request-submission-package-${submission.id}-state`}
                           appearance={"tint"}
                           color={submission.withdrawn || !submission.current ? "subtle" : "success"}>
                        {packageState(submission)}
                    </Badge>
                    {!submission.withdrawn && !submission.current && (
                        <Text id={`information-request-submission-package-${submission.id}-replaced`}
                              className={styles.detail}>
                            Replaced by a later submission.
                        </Text>
                    )}
                    {answersCorrection(submission, packages) && !submission.withdrawn && (
                        <Text id={`information-request-submission-package-${submission.id}-correction`}
                              className={styles.detail}>
                            Answers a correction, so it stays submitted.
                        </Text>
                    )}
                    {submission.withdrawable && !closed && (
                        <Button id={`information-request-submission-package-${submission.id}-withdraw`}
                                appearance={"secondary"}
                                shape={"circular"}
                                disabled={busy}
                                aria-label={`Withdraw submission ${submission.packageNumber}`}
                                onClick={() => setWithdrawing(submission)}>
                            Withdraw
                        </Button>
                    )}
                </div>
            ))}
            {withdrawing && !closed && (
                <SubmissionWithdrawalDialog packageId={withdrawing.id}
                                            packageNumber={withdrawing.packageNumber}
                                            busy={busy}
                                            error={error}
                                            onWithdraw={onWithdraw}
                                            onDismiss={() => setWithdrawing(null)}/>
            )}
        </section>
    );
};

export default SubmissionPackageList;
