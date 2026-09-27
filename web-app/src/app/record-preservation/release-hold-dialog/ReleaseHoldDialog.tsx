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
    MessageBar,
    MessageBarBody,
    Text,
    Textarea,
} from "@fluentui/react-components";
import {releaseRecordPreservationHold} from "../../../services/recordPreservationService.ts";
import {normalizeApiError} from "../../../utils/apiErrorUtils.ts";
import {RecordPreservationHoldDto} from "../../models/models.tsx";
import {recordTypeLabel} from "../recordPreservationLabels.ts";
import {useReleaseHoldDialogStyles} from "./ReleaseHoldDialogStyles.tsx";

interface ReleaseHoldDialogProps
{
    hold: RecordPreservationHoldDto;
    onReleased: (hold: RecordPreservationHoldDto) => void;
    onDismiss: () => void;
}

const REASON_LIMIT = 1000;

const ReleaseHoldDialog = ({hold, onReleased, onDismiss}: ReleaseHoldDialogProps) =>
{
    const styles = useReleaseHoldDialogStyles();
    const [reason, setReason] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const release = async () =>
    {
        setBusy(true);
        setError(null);
        try
        {
            onReleased(await releaseRecordPreservationHold(hold.id, reason.trim()));
        }
        catch (caught: unknown)
        {
            setError(normalizeApiError(caught, "The hold could not be released.").message);
            setBusy(false);
        }
    };

    return (
        <Dialog open={true}
                onOpenChange={(_, data) => { if (!data.open && !busy) onDismiss(); }}>
            <DialogSurface id={"release-record-hold-dialog"}
                           className={styles.surface}>
                <DialogBody>
                    <DialogTitle id={"release-record-hold-dialog-title"}>Release preservation hold</DialogTitle>
                    <DialogContent id={"release-record-hold-dialog-content"}
                                   className={styles.content}>
                        {error && (
                            <MessageBar id={"release-record-hold-dialog-error"}
                                        intent={"error"}>
                                <MessageBarBody>{error}</MessageBarBody>
                            </MessageBar>
                        )}
                        <Text id={"release-record-hold-dialog-summary"}>
                            {`This lifts only this hold on the ${recordTypeLabel(hold.resourceType)}. Other holds still apply, and the hold and its history stay on record.`}
                        </Text>
                        <Field id={"release-record-hold-reason-field"}
                               label={"Reason"}
                               required>
                            <Textarea id={"release-record-hold-reason"}
                                      value={reason}
                                      maxLength={REASON_LIMIT}
                                      disabled={busy}
                                      onChange={(_, data) => setReason(data.value)}/>
                        </Field>
                    </DialogContent>
                    <DialogActions id={"release-record-hold-dialog-actions"}
                                   className={styles.actions}>
                        <Button id={"release-record-hold-confirm"}
                                appearance={"primary"}
                                shape={"circular"}
                                disabled={busy || !reason.trim()}
                                onClick={release}>
                            Release hold
                        </Button>
                        <Button id={"release-record-hold-cancel"}
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

export default ReleaseHoldDialog;
