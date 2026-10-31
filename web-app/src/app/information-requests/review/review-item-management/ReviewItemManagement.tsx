import {useState} from "react";
import {Button} from "@fluentui/react-components";
import {InformationRequestReviewDto, InformationRequestReviewItemDto} from "../../../models/models.tsx";
import ReviewCommentDialog from "../review-comment-dialog/ReviewCommentDialog.tsx";
import ReviewOverrideDialog from "../review-override-dialog/ReviewOverrideDialog.tsx";
import {overrideStageFor, reviewIsOpen} from "../reviewOverrideStage.ts";
import {ReviewManagement} from "../useReviewManagement.ts";
import {useReviewItemManagementStyles} from "./ReviewItemManagementStyles.tsx";

interface Props {
    id: string;
    item: InformationRequestReviewItemDto;
    label: string;
    review: InformationRequestReviewDto;
    management: ReviewManagement;
    decidable: boolean;
    onAddFinding: () => void;
}

const ReviewItemManagement = ({id, item, label, review, management, decidable, onAddFinding}: Props) => {
    const styles = useReviewItemManagementStyles();
    const [dialog, setDialog] = useState<"override" | "comment" | null>(null);
    const overrideStage = overrideStageFor(review, item.submissionItemId);
    const findsAsManager = review.canManage && !decidable && reviewIsOpen(review);
    const closeWhenRecorded = (recorded: boolean) => {
        if (recorded) setDialog(null);
    };

    return (
        <>
            <div id={`${id}-management`}
                 className={styles.row}>
                {review.canComment && (
                    <Button id={`${id}-comment`}
                            size={"small"}
                            shape={"circular"}
                            appearance={"subtle"}
                            aria-label={`Comment on ${label}`}
                            disabled={management.busy}
                            onClick={() => setDialog("comment")}>
                        Comment
                    </Button>
                )}
                {findsAsManager && (
                    <Button id={`${id}-manager-finding`}
                            size={"small"}
                            shape={"circular"}
                            appearance={"subtle"}
                            aria-label={`Add finding on ${label}`}
                            disabled={management.busy}
                            onClick={onAddFinding}>
                        Add finding
                    </Button>
                )}
                {overrideStage && (
                    <Button id={`${id}-override`}
                            size={"small"}
                            shape={"circular"}
                            aria-label={`Override ${label}`}
                            disabled={management.busy}
                            onClick={() => setDialog("override")}>
                        Override
                    </Button>
                )}
            </div>
            {dialog === "comment" && (
                <ReviewCommentDialog label={label}
                                     busy={management.busy}
                                     message={management.message}
                                     onConfirm={(body, visibility) =>
                                         void management.comment({
                                             submissionItemId: item.submissionItemId,
                                             visibility,
                                             body
                                         })
                                             .then(closeWhenRecorded)}
                                     onDismiss={() => setDialog(null)}/>
            )}
            {dialog === "override" && overrideStage && (
                <ReviewOverrideDialog busy={management.busy}
                                      message={management.message}
                                      waiverPermitted={item.waiverPermitted}
                                      onConfirm={(outcome, narrative) =>
                                          void management.override({
                                              stageKey: overrideStage.stageKey,
                                              submissionItemId: item.submissionItemId,
                                              outcome,
                                              narrative,
                                          }).then(closeWhenRecorded)}
                                      onDismiss={() => setDialog(null)}/>
            )}
        </>
    );
};

export default ReviewItemManagement;
