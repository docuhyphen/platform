import {Button, Text} from "@fluentui/react-components";
import {
    InformationRequestSubmissionGroupProblemDto,
    InformationRequestSubmissionProblemDto,
} from "../../../models/models.tsx";
import {groupProblemDetail, groupProblemLabel, humanizedKey, problemLabels} from "../submissionLabels.ts";
import {focusGroup, focusRequirement, labeledOccurrence} from "../submissionReview.ts";
import {useSubmissionReadinessListStyles} from "./SubmissionReadinessListStyles.tsx";

interface Props
{
    ready: boolean;
    problems: InformationRequestSubmissionProblemDto[];
    undisclosedProblemCount: number;
    groupProblems: InformationRequestSubmissionGroupProblemDto[];
    requirementLabels: Record<string, string>;
}

const SubmissionReadinessList = ({ready, problems, undisclosedProblemCount, groupProblems, requirementLabels}: Props) =>
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
                    const label = requirementLabels[problem.requirementId] ?? labeledOccurrence(humanizedKey(problem.requirementKey), problem.occurrencePath);
                    const id = `information-request-submission-problem-${problem.requirementId}-${problem.code.toLowerCase()}`;
                    return (
                        <li id={id}
                            key={`${problem.requirementId}-${problem.code}`}
                            className={styles.problem}>
                            <Button id={`${id}-go`}
                                    appearance={"transparent"}
                                    shape={"circular"}
                                    size={"small"}
                                    aria-label={`Go to ${label}`}
                                    onClick={() => focusRequirement(problem.occurrencePath, problem.requirementKey, problem.requirementId)}>
                                {label}
                            </Button>
                            <Text id={`${id}-detail`}
                                  className={styles.detail}>
                                {problemLabels[problem.code]}
                            </Text>
                        </li>
                    );
                })}
                {groupProblems.map((problem, index) => {
                    const id = `information-request-submission-group-problem-${index}`;
                    return (
                        <li id={id}
                            key={`${problem.groupKey}-${problem.parentOccurrencePath ?? ""}`}
                            className={styles.problem}>
                            <Button id={`${id}-label`}
                                    appearance={"transparent"}
                                    shape={"circular"}
                                    size={"small"}
                                    aria-label={`Go to ${groupProblemLabel(problem)}`}
                                    onClick={() => focusGroup(problem.groupKey, problem.parentOccurrencePath)}>
                                {groupProblemLabel(problem)}
                            </Button>
                            <Text id={`${id}-detail`}
                                  className={styles.detail}>
                                {groupProblemDetail(problem)}
                            </Text>
                        </li>
                    );
                })}
            </ul>
            {undisclosedProblemCount > 0 && (
                <Text id={"information-request-submission-undisclosed-problems"}
                      className={styles.detail}>
                    {undisclosedProblemCount === 1
                        ? "One additional item or submission rule is not complete yet."
                        : `${undisclosedProblemCount} additional items or submission rules are not complete yet.`}
                </Text>
            )}
        </div>
    );
};

export default SubmissionReadinessList;
