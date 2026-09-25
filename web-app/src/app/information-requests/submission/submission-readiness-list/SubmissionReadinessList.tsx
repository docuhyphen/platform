import {Text} from "@fluentui/react-components";
import {InformationRequestSubmissionProblemDto} from "../../../models/models.tsx";
import {humanizedKey, problemLabels} from "../submissionLabels.ts";
import {useSubmissionReadinessListStyles} from "./SubmissionReadinessListStyles.tsx";

interface Props
{
    ready: boolean;
    problems: InformationRequestSubmissionProblemDto[];
    undisclosedProblemCount: number;
}

const SubmissionReadinessList = ({ready, problems, undisclosedProblemCount}: Props) =>
{
    const styles = useSubmissionReadinessListStyles();

    if (ready)
    {
        return (
            <Text id={"information-request-submission-ready"}
                  className={styles.ready}>
                Everything this part asks for is complete.
            </Text>
        );
    }

    return (
        <div id={"information-request-submission-readiness"}
             className={styles.list}>
            <Text id={"information-request-submission-readiness-title"}
                  weight={"semibold"}>
                Before you submit
            </Text>
            <ul id={"information-request-submission-problems"}
                className={styles.problems}>
                {problems.map(problem => (
                    <li id={`information-request-submission-problem-${problem.requirementId}`}
                        key={`${problem.requirementId}-${problem.code}`}
                        className={styles.problem}>
                        <Text weight={"semibold"}>{humanizedKey(problem.requirementKey)}</Text>
                        <Text className={styles.detail}>{problemLabels[problem.code]}</Text>
                    </li>
                ))}
            </ul>
            {undisclosedProblemCount > 0 && (
                <Text id={"information-request-submission-undisclosed-problems"}
                      className={styles.detail}>
                    {undisclosedProblemCount === 1
                        ? "One more item handled by another party is not complete yet."
                        : `${undisclosedProblemCount} more items handled by other parties are not complete yet.`}
                </Text>
            )}
        </div>
    );
};

export default SubmissionReadinessList;
