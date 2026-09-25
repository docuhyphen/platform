import {Badge, Button, Text} from "@fluentui/react-components";
import {InformationRequestSubmissionPackageDto} from "../../../models/models.tsx";
import {humanizedKey} from "../submissionLabels.ts";
import {useSubmissionPackageListStyles} from "./SubmissionPackageListStyles.tsx";

interface Props
{
    packages: InformationRequestSubmissionPackageDto[];
    busy: boolean;
    closed: boolean;
    onWithdraw: (packageId: string) => void;
}

const SubmissionPackageList = ({packages, busy, closed, onWithdraw}: Props) =>
{
    const styles = useSubmissionPackageListStyles();

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
                        <Text weight={"semibold"}>
                            {`Submission ${submission.packageNumber}`}
                            {submission.stageKey && ` - ${humanizedKey(submission.stageKey)}`}
                        </Text>
                        <Text className={styles.detail}>
                            {new Date(submission.submittedAt).toLocaleString()}
                        </Text>
                    </div>
                    <Badge id={`information-request-submission-package-${submission.id}-state`}
                           appearance={"tint"}
                           color={submission.withdrawn ? "subtle" : "success"}>
                        {submission.withdrawn ? "Withdrawn" : "Submitted"}
                    </Badge>
                    {submission.submittedByCaller && !submission.withdrawn && !closed && (
                        <Button id={`information-request-submission-package-${submission.id}-withdraw`}
                                appearance={"secondary"}
                                shape={"circular"}
                                disabled={busy}
                                onClick={() => onWithdraw(submission.id)}>
                            Withdraw
                        </Button>
                    )}
                </div>
            ))}
        </section>
    );
};

export default SubmissionPackageList;
