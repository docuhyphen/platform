import {Button, Text} from "@fluentui/react-components";
import {InformationRequestReviewAssignmentChange} from "../../../../services/informationRequestReviewService.ts";
import {InformationRequestReviewAssignmentDto, InformationRequestReviewAssignmentState} from "../../../models/models.tsx";
import {useReviewAssignmentsPanelStyles} from "../review-assignments-panel/ReviewAssignmentsPanelStyles.tsx";

interface Props
{
    assignment: InformationRequestReviewAssignmentDto;
    reviewerLabel: string;
    stageTitle: string;
    canManage: boolean;
    onChange: (change: InformationRequestReviewAssignmentChange, title: string) => void;
}

const STATE_LABELS: Record<InformationRequestReviewAssignmentState, string> = {
    [InformationRequestReviewAssignmentState.ACTIVE]: "Assigned",
    [InformationRequestReviewAssignmentState.RECUSED]: "Recused",
    [InformationRequestReviewAssignmentState.DELEGATED]: "Delegated",
    [InformationRequestReviewAssignmentState.REVOKED]: "Removed",
};

const ReviewAssignmentRow = ({assignment, reviewerLabel, stageTitle, canManage, onChange}: Props) =>
{
    const styles = useReviewAssignmentsPanelStyles();
    const active = assignment.state === InformationRequestReviewAssignmentState.ACTIVE && !assignment.decidedAt;
    const id = `information-request-review-assignment-${assignment.id}`;

    return (
        <li id={id}
            className={styles.assignment}>
            <div id={`${id}-text`}>
                <Text weight={"semibold"}>{reviewerLabel}</Text>
                <Text size={200}
                      className={styles.detail}>
                    {` ${stageTitle}. ${STATE_LABELS[assignment.state]}`}
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
                    {assignment.callerIsReviewer && (
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
