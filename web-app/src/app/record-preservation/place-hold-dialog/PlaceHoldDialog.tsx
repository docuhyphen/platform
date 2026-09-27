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
    Input,
    MessageBar,
    MessageBarBody,
    Radio,
    RadioGroup,
    Textarea,
} from "@fluentui/react-components";
import {placeRecordPreservationHold} from "../../../services/recordPreservationService.ts";
import {normalizeApiError} from "../../../utils/apiErrorUtils.ts";
import {RecordPreservationHoldDto, RecordPreservationScope} from "../../models/models.tsx";
import {holdScopeLabels} from "../recordPreservationLabels.ts";
import {usePlaceHoldDialogStyles} from "./PlaceHoldDialogStyles.tsx";

interface PlaceHoldDialogProps
{
    resourceType: string;
    resourceId: string;
    onPlaced: (hold: RecordPreservationHoldDto) => void;
    onDismiss: () => void;
}

const REASON_LIMIT = 1000;

const PlaceHoldDialog = ({resourceType, resourceId, onPlaced, onDismiss}: PlaceHoldDialogProps) =>
{
    const styles = usePlaceHoldDialogStyles();
    const [reason, setReason] = useState("");
    const [caseReference, setCaseReference] = useState("");
    const [scope, setScope] = useState<RecordPreservationScope>(RecordPreservationScope.DESCENDANTS_AND_REFERENCES);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const place = async () =>
    {
        setBusy(true);
        setError(null);
        try
        {
            onPlaced(await placeRecordPreservationHold({
                resourceType,
                resourceId,
                scope,
                reason: reason.trim(),
                caseReference: caseReference.trim() || undefined,
            }));
        }
        catch (caught: unknown)
        {
            setError(normalizeApiError(caught, "The hold could not be placed.").message);
            setBusy(false);
        }
    };

    return (
        <Dialog open={true}
                onOpenChange={(_, data) => { if (!data.open && !busy) onDismiss(); }}>
            <DialogSurface id={"place-record-hold-dialog"}
                           className={styles.surface}>
                <DialogBody>
                    <DialogTitle id={"place-record-hold-dialog-title"}>Place a preservation hold</DialogTitle>
                    <DialogContent id={"place-record-hold-dialog-content"}
                                   className={styles.content}>
                        {error && (
                            <MessageBar id={"place-record-hold-dialog-error"}
                                        intent={"error"}>
                                <MessageBarBody>{error}</MessageBarBody>
                            </MessageBar>
                        )}
                        <Field id={"place-record-hold-reason-field"}
                               label={"Reason"}
                               required>
                            <Textarea id={"place-record-hold-reason"}
                                      value={reason}
                                      maxLength={REASON_LIMIT}
                                      disabled={busy}
                                      onChange={(_, data) => setReason(data.value)}/>
                        </Field>
                        <Field id={"place-record-hold-case-field"}
                               label={"Case reference"}>
                            <Input id={"place-record-hold-case"}
                                   value={caseReference}
                                   disabled={busy}
                                   onChange={(_, data) => setCaseReference(data.value)}/>
                        </Field>
                        <Field id={"place-record-hold-scope-field"}
                               label={"Covers"}>
                            <RadioGroup id={"place-record-hold-scope"}
                                        value={scope}
                                        disabled={busy}
                                        onChange={(_, data) =>
                                            setScope(Object.values(RecordPreservationScope).find(candidate => candidate === data.value) ?? scope)}>
                                {Object.values(RecordPreservationScope).map(candidate => (
                                    <Radio key={candidate}
                                           id={`place-record-hold-scope-${candidate.toLowerCase()}`}
                                           value={candidate}
                                           label={holdScopeLabels[candidate]}/>
                                ))}
                            </RadioGroup>
                        </Field>
                    </DialogContent>
                    <DialogActions id={"place-record-hold-dialog-actions"}
                                   className={styles.actions}>
                        <Button id={"place-record-hold-confirm"}
                                appearance={"primary"}
                                shape={"circular"}
                                disabled={busy || !reason.trim()}
                                onClick={place}>
                            Place hold
                        </Button>
                        <Button id={"place-record-hold-cancel"}
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

export default PlaceHoldDialog;
