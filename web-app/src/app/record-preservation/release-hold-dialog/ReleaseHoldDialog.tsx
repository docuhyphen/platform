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
import {useStableIdempotencyKey} from "../../information-requests/shared/useStableIdempotencyKey.ts";
import {isStaleInformationRequestCommand} from "../../information-requests/shared/informationRequestRefusal.ts";

interface ReleaseHoldDialogProps
{
    hold: RecordPreservationHoldDto;
    onReleased: (hold: RecordPreservationHoldDto) => void;
    onStale: () => void;
    onDismiss: () => void;
}

const REASON_LIMIT = 2048;

const ReleaseHoldDialog = ({hold, onReleased, onStale, onDismiss}: ReleaseHoldDialogProps) =>
{
    const styles = useReleaseHoldDialogStyles();
    const [reason, setReason] = useState("");
    const [busy, setBusy] = useState(false);
    const keyed = useStableIdempotencyKey();
    const [error, setError] = useState<string | null>(null);

    const release = async () =>
    {
        setBusy(true);
        setError(null);
        try
        {
            onReleased(await keyed(`release:${hold.id}:${hold.holdETag}`, reason.trim(), key =>
                releaseRecordPreservationHold(hold.id, reason.trim(), hold.holdETag, key)));
        }
        catch (caught: unknown)
        {
            if (isStaleInformationRequestCommand(caught)) {
                onStale();
                return;
            }
            setError(normalizeApiError(caught, "The hold could not be released.").message);
            setBusy(false);
        }
    };

    return (
        <Dialog open={true}
                onOpenChange={(_, data) => { if (!data.open && !busy) onDismiss(); }}>
            <DialogSurface id={"release-record-hold-dialog"}
                           aria-labelledby={"release-record-hold-dialog-title"}
                           className={styles.surface}>
                <DialogBody id={"release-record-hold-dialog-body"}>
                    <DialogTitle id={"release-record-hold-dialog-title"}>Release preservation hold</DialogTitle>
                    <DialogContent id={"release-record-hold-dialog-content"}
                                   className={styles.content}>
                        {error && (
                            <MessageBar id={"release-record-hold-dialog-error"}
                                        intent={"error"}
                                        role={"alert"}>
                                <MessageBarBody id={"release-record-hold-dialog-error-body"}>{error}</MessageBarBody>
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
