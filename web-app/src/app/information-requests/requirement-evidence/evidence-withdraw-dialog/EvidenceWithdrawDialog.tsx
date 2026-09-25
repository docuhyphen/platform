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
import {useEvidenceWithdrawDialogStyles} from "./EvidenceWithdrawDialogStyles.tsx";

interface Props
{
    id: string;
    fileName: string;
    busy: boolean;
    onConfirm: (reason: string) => void;
    onDismiss: () => void;
}

const REASON_LIMIT = 500;

const EvidenceWithdrawDialog = ({id, fileName, busy, onConfirm, onDismiss}: Props) =>
{
    const styles = useEvidenceWithdrawDialogStyles();
    const [reason, setReason] = useState("");

    return (
        <Dialog open={true}
                onOpenChange={(_, data) => { if (!data.open) onDismiss(); }}>
            <DialogSurface id={id}
                           className={styles.surface}>
                <DialogBody>
                    <DialogTitle id={`${id}-title`}>Withdraw file</DialogTitle>
                    <DialogContent id={`${id}-content`}
                                   className={styles.content}>
                        <Text id={`${id}-summary`}>
                            {fileName} will no longer count toward this Requirement. It stays on record.
                        </Text>
                        <Field id={`${id}-reason-field`}
                               label="Reason"
                               required>
                            <Textarea id={`${id}-reason`}
                                      value={reason}
                                      maxLength={REASON_LIMIT}
                                      disabled={busy}
                                      onChange={(_, data) => setReason(data.value)}/>
                        </Field>
                    </DialogContent>
                    <DialogActions id={`${id}-actions`}
                                   className={styles.actions}>
                        <Button id={`${id}-confirm`}
                                appearance="primary"
                                shape="circular"
                                disabled={busy || !reason.trim()}
                                onClick={() => onConfirm(reason.trim())}>
                            Withdraw
                        </Button>
                        <Button id={`${id}-cancel`}
                                appearance="secondary"
                                shape="circular"
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

export default EvidenceWithdrawDialog;
