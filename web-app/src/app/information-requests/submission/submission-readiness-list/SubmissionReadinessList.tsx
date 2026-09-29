import {Button, Text} from "@fluentui/react-components";
import {InformationRequestSubmissionProblemDto} from "../../../models/models.tsx";
import {humanizedKey, problemLabels} from "../submissionLabels.ts";
import {focusRequirement} from "../submissionReview.ts";
import {useSubmissionReadinessListStyles} from "./SubmissionReadinessListStyles.tsx";

interface Props
{
    ready: boolean;
    problems: InformationRequestSubmissionProblemDto[];
    undisclosedProblemCount: number;
    requirementLabels: Record<string, string>;
}

const SubmissionReadinessList = ({ready, problems, undisclosedProblemCount, requirementLabels}: Props) =>
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
                {problems.map(problem =>
                {
                    const label = requirementLabels[problem.requirementId] ?? humanizedKey(problem.requirementKey);
                    return (
                        <li id={`information-request-submission-problem-${problem.requirementId}`}
                            key={`${problem.requirementId}-${problem.code}`}
                            className={styles.problem}>
                            <Button id={`information-request-submission-problem-${problem.requirementId}-go`}
                                    appearance={"transparent"}
                                    shape={"circular"}
                                    size={"small"}
                                    aria-label={`Go to ${label}`}
                                    onClick={() => focusRequirement(problem.occurrencePath, problem.requirementKey)}>
                                {label}
                            </Button>
                            <Text className={styles.detail}>{problemLabels[problem.code]}</Text>
                        </li>
                    );
                })}
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
