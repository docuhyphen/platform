import {useState} from "react";
import {Button, Title3} from "@fluentui/react-components";
import {InformationRequestReviewAssignmentChange} from "../../../../services/informationRequestReviewService.ts";
import {
    InformationRequestReviewAssignmentDto,
    InformationRequestReviewDto,
    InformationRequestShareRoleKey,
} from "../../../models/models.tsx";
import AssignReviewerDialog from "../assign-reviewer-dialog/AssignReviewerDialog.tsx";
import AssignmentChangeDialog from "../assignment-change-dialog/AssignmentChangeDialog.tsx";
import ReviewAssignmentRow from "../review-assignment-row/ReviewAssignmentRow.tsx";
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
    const [pending, setPending] = useState<PendingChange | null>(null);
    const stageTitle = (stageKey: string) => review.stages.find(stage => stage.stageKey === stageKey)?.title ?? stageKey;
    const reviewerLabel = (assignment: InformationRequestReviewAssignmentDto) =>
    {
        const label = management.parties.find(party => party.id === assignment.reviewerPartyId)?.label ?? "Reviewer";
        return assignment.callerIsReviewer ? `${label} (you)` : label;
    };
    const delegates = management.parties.filter(party => party.roleKey === InformationRequestShareRoleKey.REVIEWER);

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
                {review.canManage && (
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
                                         onChange={(change, title) => setPending({assignment, change, title})}/>
                ))}
            </ul>
            {assigning && (
                <AssignReviewerDialog stages={review.stages}
                                      parties={management.parties}
                                      busy={management.busy}
                                      onConfirm={request =>
                                      {
                                          setAssigning(false);
                                          void management.assign(request);
                                      }}
                                      onDismiss={() => setAssigning(false)}/>
            )}
            {pending && (
                <AssignmentChangeDialog title={pending.title}
                                        change={pending.change}
                                        delegates={delegates.filter(party => party.id !== pending.assignment.reviewerPartyId)}
                                        busy={management.busy}
                                        onConfirm={request =>
                                        {
                                            setPending(null);
                                            void management.changeAssignment(pending.assignment.id, pending.change, request);
                                        }}
                                        onDismiss={() => setPending(null)}/>
            )}
        </section>
    );
};

export default ReviewAssignmentsPanel;
