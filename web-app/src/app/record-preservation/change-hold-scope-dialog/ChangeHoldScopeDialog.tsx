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
    Radio,
    RadioGroup,
    Text,
    Textarea,
} from "@fluentui/react-components";
import {changeRecordPreservationHoldScope} from "../../../services/recordPreservationService.ts";
import {normalizeApiError} from "../../../utils/apiErrorUtils.ts";
import {RecordPreservationHoldDto, RecordPreservationScope} from "../../models/models.tsx";
import {holdScopeLabels} from "../recordPreservationLabels.ts";
import {useChangeHoldScopeDialogStyles} from "./ChangeHoldScopeDialogStyles.tsx";

interface ChangeHoldScopeDialogProps
{
    hold: RecordPreservationHoldDto;
    onChanged: (hold: RecordPreservationHoldDto) => void;
    onDismiss: () => void;
}

const REASON_LIMIT = 1000;

const ChangeHoldScopeDialog = ({hold, onChanged, onDismiss}: ChangeHoldScopeDialogProps) =>
{
    const styles = useChangeHoldScopeDialogStyles();
    const [scope, setScope] = useState<RecordPreservationScope>(hold.scope);
    const [reason, setReason] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const change = async () =>
    {
        setBusy(true);
        setError(null);
        try
        {
            onChanged(await changeRecordPreservationHoldScope(hold.id, scope, reason.trim()));
        }
        catch (caught: unknown)
        {
            setError(normalizeApiError(caught, "The hold's scope could not be changed.").message);
            setBusy(false);
        }
    };

    return (
        <Dialog open={true}
                onOpenChange={(_, data) => { if (!data.open && !busy) onDismiss(); }}>
            <DialogSurface id={"change-record-hold-scope-dialog"}
                           aria-labelledby={"change-record-hold-scope-dialog-title"}
                           className={styles.surface}>
                <DialogBody>
                    <DialogTitle id={"change-record-hold-scope-dialog-title"}>Change what this hold covers</DialogTitle>
                    <DialogContent id={"change-record-hold-scope-dialog-content"}
                                   className={styles.content}>
                        {error && (
                            <MessageBar id={"change-record-hold-scope-dialog-error"}
                                        intent={"error"}>
                                <MessageBarBody>{error}</MessageBarBody>
                            </MessageBar>
                        )}
                        <Text id={"change-record-hold-scope-dialog-summary"}>
                            The change is kept in the hold&apos;s history with its reason.
                        </Text>
                        <Field id={"change-record-hold-scope-field"}
                               label={"Covers"}>
                            <RadioGroup id={"change-record-hold-scope"}
                                        value={scope}
                                        disabled={busy}
                                        onChange={(_, data) =>
                                            setScope(Object.values(RecordPreservationScope).find(candidate => candidate === data.value) ?? scope)}>
                                {Object.values(RecordPreservationScope).map(candidate => (
                                    <Radio key={candidate}
                                           id={`change-record-hold-scope-${candidate.toLowerCase()}`}
                                           value={candidate}
                                           label={holdScopeLabels[candidate]}/>
                                ))}
                            </RadioGroup>
                        </Field>
                        <Field id={"change-record-hold-scope-reason-field"}
                               label={"Reason"}
                               required>
                            <Textarea id={"change-record-hold-scope-reason"}
                                      value={reason}
                                      maxLength={REASON_LIMIT}
                                      disabled={busy}
                                      onChange={(_, data) => setReason(data.value)}/>
                        </Field>
                    </DialogContent>
                    <DialogActions id={"change-record-hold-scope-dialog-actions"}
                                   className={styles.actions}>
                        <Button id={"change-record-hold-scope-confirm"}
                                appearance={"primary"}
                                shape={"circular"}
                                disabled={busy || !reason.trim() || scope === hold.scope}
                                onClick={change}>
                            Change scope
                        </Button>
                        <Button id={"change-record-hold-scope-cancel"}
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

export default ChangeHoldScopeDialog;
