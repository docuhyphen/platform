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
import {useInformationRequestSupplementDialogStyles} from "./InformationRequestSupplementDialogStyles.tsx";

interface Props
{
    busy: boolean;
    onConfirm: (reason: string) => void;
    onDismiss: () => void;
}

const REASON_LIMIT = 128;

const InformationRequestSupplementDialog = ({busy, onConfirm, onDismiss}: Props) =>
{
    const styles = useInformationRequestSupplementDialogStyles();
    const [reason, setReason] = useState("");

    return (
        <Dialog open={true}
                onOpenChange={(_, data) => { if (!data.open) onDismiss(); }}>
            <DialogSurface id={"information-request-supplement-dialog"}
                           className={styles.surface}>
                <DialogBody>
                    <DialogTitle id={"information-request-supplement-dialog-title"}>Request more information</DialogTitle>
                    <DialogContent id={"information-request-supplement-dialog-content"}
                                   className={styles.content}>
                        <Text id={"information-request-supplement-dialog-summary"}>
                            A new draft request follows this one. The submitted package stays as it is, and the same parties can answer again.
                        </Text>
                        <Field id={"information-request-supplement-reason-field"}
                               label={"Reason"}>
                            <Textarea id={"information-request-supplement-reason"}
                                      value={reason}
                                      maxLength={REASON_LIMIT}
                                      disabled={busy}
                                      onChange={(_, data) => setReason(data.value)}/>
                        </Field>
                    </DialogContent>
                    <DialogActions id={"information-request-supplement-dialog-actions"}
                                   className={styles.actions}>
                        <Button id={"information-request-supplement-confirm"}
                                appearance={"primary"}
                                shape={"circular"}
                                disabled={busy}
                                onClick={() => onConfirm(reason.trim())}>
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
