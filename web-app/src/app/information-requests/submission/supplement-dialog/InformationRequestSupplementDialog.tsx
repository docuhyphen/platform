import ReviewDialogFeedback from "../../review/review-dialog-feedback/ReviewDialogFeedback.tsx";
import {useState} from "react";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Text,
} from "@fluentui/react-components";
import {SUPPLEMENT_REASON_OPTIONS} from "../../authoring/requestReasonCodes.ts";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {useInformationRequestSupplementDialogStyles} from "./InformationRequestSupplementDialogStyles.tsx";

interface Props
{
    busy: boolean;
    error?: string | null;
    onConfirm: (reason: string) => void;
    onDismiss: () => void;
}

const InformationRequestSupplementDialog = ({busy, error, onConfirm, onDismiss}: Props) =>
{
    const styles = useInformationRequestSupplementDialogStyles();
    const [reason, setReason] = useState("");

    return (
        <Dialog open={true}
                onOpenChange={(_, data) => {
                    if (!data.open) onDismiss();
                }}>
            <DialogSurface id={"information-request-supplement-dialog"}
                           aria-labelledby={"information-request-supplement-dialog-title"}
                           className={styles.surface}>
                <DialogBody id={"information-request-supplement-dialog-body"}>
                    <DialogTitle id={"information-request-supplement-dialog-title"}>Request more
                        information</DialogTitle>
                    <DialogContent id={"information-request-supplement-dialog-content"}
                                   className={styles.content}>
                        <Text id={"information-request-supplement-dialog-summary"}>
                            A new draft request follows this one. The submitted package stays as it is, and the same
                            parties can answer again.
                        </Text>
                        <ChoiceSelect id={"information-request-supplement-reason"}
                                      label={"Reason"}
                                      value={reason}
                                      placeholder={"Choose a reason"}
                                      hint={"Recorded in the request history."}
                                      options={SUPPLEMENT_REASON_OPTIONS}
                                      disabled={busy}
                                      onChange={setReason}/>
                        <ReviewDialogFeedback id={"information-request-supplement-error"}
                                              message={error ? {intent: "error", text: error} : null}/>
                    </DialogContent>
                    <DialogActions id={"information-request-supplement-dialog-actions"}
                                   className={styles.actions}>
                        <Button id={"information-request-supplement-confirm"}
                                appearance={"primary"}
                                shape={"circular"}
                                disabled={busy || !reason}
                                onClick={() => onConfirm(reason)}>
                            Create supplement
                        </Button>
                        <Button id={"information-request-supplement-cancel"}
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

export default InformationRequestSupplementDialog;
