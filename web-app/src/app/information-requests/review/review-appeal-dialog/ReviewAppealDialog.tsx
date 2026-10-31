import {useState} from "react";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Field,
    Text,
    Textarea,
} from "@fluentui/react-components";
import ReviewDialogFeedback from "../review-dialog-feedback/ReviewDialogFeedback.tsx";
import {ReviewMessage} from "../useReviewFeedback.ts";
import {useReviewAppealDialogStyles} from "./ReviewAppealDialogStyles.tsx";

interface Props
{
    busy: boolean;
    message?: ReviewMessage | null;
    onConfirm: (reason: string) => void;
    onDismiss: () => void;
}

const REASON_LIMIT = 2000;

const ReviewAppealDialog = ({busy, message, onConfirm, onDismiss}: Props) =>
{
    const styles = useReviewAppealDialogStyles();
    const [reason, setReason] = useState("");

    return (
        <Dialog open={true}
                onOpenChange={(_, data) => {
                    if (!data.open) onDismiss();
                }}>
            <DialogSurface id={"information-request-review-appeal-dialog"}
                           aria-labelledby={"information-request-review-appeal-dialog-title"}
                           className={styles.surface}>
                <DialogBody id={"information-request-review-appeal-dialog-body"}>
                    <DialogTitle id={"information-request-review-appeal-dialog-title"}>Appeal this review</DialogTitle>
                    <DialogContent id={"information-request-review-appeal-dialog-content"}
                                   className={styles.content}>
                        <Text id={"information-request-review-appeal-dialog-summary"}>
                            A new review decides your appeal. The earlier review and its findings stay on record.
                        </Text>
                        <ReviewDialogFeedback id={"information-request-review-appeal-refusal"}
                                              message={message}/>
                        <Field id={"information-request-review-appeal-reason-field"}
                               label={"Why the decision should change"}>
                            <Textarea id={"information-request-review-appeal-reason"}
                                      value={reason}
                                      maxLength={REASON_LIMIT}
                                      disabled={busy}
                                      onChange={(_, data) => setReason(data.value)}/>
                        </Field>
                    </DialogContent>
                    <DialogActions id={"information-request-review-appeal-dialog-actions"}
                                   className={styles.actions}>
                        <Button id={"information-request-review-appeal-confirm"}
                                appearance={"primary"}
                                shape={"circular"}
                                disabled={busy || reason.trim().length === 0}
                                onClick={() => onConfirm(reason.trim())}>
                            Send appeal
                        </Button>
                        <Button id={"information-request-review-appeal-cancel"}
                                appearance={"secondary"}
                                shape={"circular"}
                                disabled={busy}
                                onClick={onDismiss}>
                            Cancel
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default ReviewAppealDialog;
