import {Button, Text} from "@fluentui/react-components";
import {InformationRequestReviewAssignmentChange} from "../../../../services/informationRequestReviewService.ts";
import {
    InformationRequestReviewAssignmentDto,
    InformationRequestReviewAssignmentState
} from "../../../models/models.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import {useReviewAssignmentRowStyles} from "./ReviewAssignmentRowStyles.tsx";

interface Props
{
    assignment: InformationRequestReviewAssignmentDto;
    reviewerLabel: string;
    stageTitle: string;
    canManage: boolean;
    mutable?: boolean;
    onChange: (change: InformationRequestReviewAssignmentChange, title: string) => void;
}

const STATE_LABELS: Record<InformationRequestReviewAssignmentState, string> = {
    [InformationRequestReviewAssignmentState.ACTIVE]: "Assigned",
    [InformationRequestReviewAssignmentState.RECUSED]: "Recused",
    [InformationRequestReviewAssignmentState.DELEGATED]: "Delegated",
    [InformationRequestReviewAssignmentState.REVOKED]: "Removed",
};

const ReviewAssignmentRow = ({assignment, reviewerLabel, stageTitle, canManage, mutable = true, onChange}: Props) =>
{
    const styles = useReviewAssignmentRowStyles();
    const active = mutable && assignment.state === InformationRequestReviewAssignmentState.ACTIVE && !assignment.decidedAt;
    const id = `information-request-review-assignment-${assignment.id}`;
    const detail = [stageTitle, STATE_LABELS[assignment.state]];
    if (assignment.changedAt) detail.push(`Changed ${formatInformationRequestTime(assignment.changedAt)}`);
    if (assignment.changeReasonCode) detail.push(`Reason: ${assignment.changeReasonCode}`);
    if (assignment.changeNarrative) detail.push(assignment.changeNarrative);
    if (assignment.delegatedFromAssignmentId) detail.push("Delegated from an earlier assignment");
    if (assignment.dueAt) detail.push(`Due ${formatInformationRequestTime(assignment.dueAt)}`);

    return (
        <li id={id}
            className={styles.assignment}>
            <div id={`${id}-text`}>
                <Text id={`${id}-reviewer`}
                      weight={"semibold"}>
                    {reviewerLabel}
                </Text>
                <Text id={`${id}-detail`}
                      size={200}
                      className={styles.detail}>
                    {` ${detail.join(". ")}`}
                </Text>
            </div>
            {active && (
                <div id={`${id}-actions`}
                     className={styles.actions}>
                    {assignment.callerIsReviewer && (
                        <Button id={`${id}-recuse`}
                                size={"small"}
                                shape={"circular"}
                                aria-label={`Recuse from ${stageTitle}`}
                                onClick={() => onChange("recusal", `Recuse from ${stageTitle}`)}>
                            Recuse
                        </Button>
                    )}
                    {(assignment.callerIsReviewer || canManage) && assignment.delegationCandidates.length > 0 && (
                        <Button id={`${id}-delegate`}
                                size={"small"}
                                shape={"circular"}
                                aria-label={`Delegate ${stageTitle}`}
                                onClick={() => onChange("delegation", `Delegate ${stageTitle}`)}>
                            Delegate
                        </Button>
                    )}
                    {canManage && (
                        <Button id={`${id}-revoke`}
                                size={"small"}
                                shape={"circular"}
                                appearance={"subtle"}
                                aria-label={`Remove ${reviewerLabel} from ${stageTitle}`}
                                onClick={() => onChange("revocation", `Remove ${reviewerLabel} from ${stageTitle}`)}>
                            Remove
                        </Button>
                    )}
                </div>
            )}
        </li>
    );
};

export default ReviewAssignmentRow;
