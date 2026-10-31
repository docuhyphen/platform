import {useState} from "react";
import {Button, Title3} from "@fluentui/react-components";
import {InformationRequestReviewAssignmentChange} from "../../../../services/informationRequestReviewService.ts";
import {
    InformationRequestReviewAssignmentDto,
    InformationRequestReviewDto,
    InformationRequestReviewStageState
} from "../../../models/models.tsx";
import AssignReviewerDialog from "../assign-reviewer-dialog/AssignReviewerDialog.tsx";
import AssignmentChangeDialog from "../assignment-change-dialog/AssignmentChangeDialog.tsx";
import ReviewAssignmentRow from "../review-assignment-row/ReviewAssignmentRow.tsx";
import {reviewIsOpen} from "../reviewOverrideStage.ts";
import {ReviewManagement} from "../useReviewManagement.ts";
import {useReviewAssignmentsPanelStyles} from "./ReviewAssignmentsPanelStyles.tsx";

interface Props
{
    review: InformationRequestReviewDto;
    management: ReviewManagement;
}

interface PendingChange
{
    assignment: InformationRequestReviewAssignmentDto;
    change: InformationRequestReviewAssignmentChange;
    title: string;
}

const ReviewAssignmentsPanel = ({review, management}: Props) =>
{
    const styles = useReviewAssignmentsPanelStyles();
    const [assigning, setAssigning] = useState(false);
    const unsettledStages = review.stages.filter(stage => stage.state !== InformationRequestReviewStageState.SETTLED);
    const open = reviewIsOpen(review);
    const [pending, setPending] = useState<PendingChange | null>(null);
    const stageTitle = (stageKey: string) => review.stages.find(stage => stage.stageKey === stageKey)?.title ?? stageKey;
    const reviewerLabel = (assignment: InformationRequestReviewAssignmentDto) =>
    {
        const label = management.parties.find(party => party.id === assignment.reviewerPartyId)?.label ?? "Reviewer";
        return assignment.callerIsReviewer ? `${label} (you)` : label;
    };

    return (
        <section id={"information-request-review-assignments"}
                 aria-labelledby={"information-request-review-assignments-title"}
                 className={styles.panel}>
            <div id={"information-request-review-assignments-header"}
                 className={styles.header}>
                <Title3 id={"information-request-review-assignments-title"}
                        as={"h2"}>
                    Reviewers
                </Title3>
                {review.canManage && open && unsettledStages.length > 0 && (
                    <Button id={"information-request-review-assignments-add"}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={management.busy}
                            onClick={() => setAssigning(true)}>
                        Assign reviewer
                    </Button>
                )}
            </div>
            <ul id={"information-request-review-assignments-list"}
                className={styles.list}>
                {review.assignments.map(assignment => (
                    <ReviewAssignmentRow key={assignment.id}
                                         assignment={assignment}
                                         reviewerLabel={reviewerLabel(assignment)}
                                         stageTitle={stageTitle(assignment.stageKey)}
                                         canManage={review.canManage}
                                         mutable={open && unsettledStages.some(stage => stage.stageKey === assignment.stageKey)}
                                         onChange={(change, title) => setPending({assignment, change, title})}/>
                ))}
            </ul>
            {assigning && (
                <AssignReviewerDialog stages={unsettledStages}
                                      parties={management.parties}
                                      partiesError={management.partiesError}
                                      partiesLoading={management.partiesLoading}
                                      onRetryParties={() => void management.retryParties()}
                                      busy={management.busy}
                                      message={management.message}
                                      onConfirm={request => void management.assign(request)
                                          .then(done => done && setAssigning(false))}
                                      onDismiss={() => setAssigning(false)}/>
            )}
            {pending && (
                <AssignmentChangeDialog title={pending.title}
                                        change={pending.change}
                                        candidates={pending.assignment.delegationCandidates}
                                        busy={management.busy}
                                        message={management.message}
                                        onConfirm={request => void management.changeAssignment(pending.assignment.id, pending.change, request)
                                            .then(done => done && setPending(null))}
                                        onDismiss={() => setPending(null)}/>
            )}
        </section>
    );
};

export default ReviewAssignmentsPanel;
