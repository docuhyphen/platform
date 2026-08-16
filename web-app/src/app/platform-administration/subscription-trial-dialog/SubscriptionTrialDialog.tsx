import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    MessageBar,
    MessageBarBody,
    Spinner,
} from "@fluentui/react-components";
import SubscriptionTrialFields from "./SubscriptionTrialFields.tsx";
import {useSubscriptionTrialDialogStyles} from "./SubscriptionTrialDialogStyles.tsx";
import {useSubscriptionTrialDialog} from "./useSubscriptionTrialDialog.ts";

interface SubscriptionTrialDialogProps
{
    open: boolean;
    ownerName: string;
    ownerKind: "user" | "organization";
    isExtension: boolean;
    currentPeriodEnd: string | null;
    onDismiss: () => void;
    onSaved: () => void;
    onStart: (durationDays: number, seatCapacity: number | null, reason: string) => Promise<unknown>;
    onExtend: (currentPeriodEnd: string, reason: string) => Promise<unknown>;
}

const SubscriptionTrialDialog = ({
    open,
    ownerName,
    ownerKind,
    isExtension,
    currentPeriodEnd,
    onDismiss,
    onSaved,
    onStart,
    onExtend,
}: SubscriptionTrialDialogProps) =>
{
    const styles = useSubscriptionTrialDialogStyles();
    const idPrefix = `platform-${ownerKind}-subscription-trial`;
    const dialog = useSubscriptionTrialDialog({
        open,
        ownerKind,
        isExtension,
        currentPeriodEnd,
        onDismiss,
        onSaved,
        onStart,
        onExtend,
    });
    return (
        <Dialog
            open={open}
            onOpenChange={(_, data) => !data.open && !dialog.saving && onDismiss()}>
            <DialogSurface id={`${idPrefix}-surface`}>
                <DialogBody
                    id={`${idPrefix}-body`}
                    className={styles.dialogBody}>
                    <DialogTitle id={`${idPrefix}-title`}>
                        {dialog.actionLabel} for {ownerName}
                    </DialogTitle>
                    <DialogContent
                        id={`${idPrefix}-content`}
                        className={styles.content}>
                        {dialog.error && (
                            <MessageBar
                                id={`${idPrefix}-error`}
                                intent={"error"}>
                                <MessageBarBody id={`${idPrefix}-error-body`}>{dialog.error}</MessageBarBody>
                            </MessageBar>
                        )}
                        <SubscriptionTrialFields
                            idPrefix={idPrefix}
                            fieldGridClassName={styles.fieldGrid}
                            dialog={dialog}/>
                    </DialogContent>
                    <DialogActions
                        id={`${idPrefix}-actions`}
                        className={styles.footer}>
                        <Button
                            id={`${idPrefix}-confirm`}
                            appearance={"primary"}
                            shape={"circular"}
                            disabled={dialog.saving}
                            onClick={() => void dialog.save()}>
                            {dialog.saving ? <Spinner size={"tiny"}/> : dialog.actionLabel}
                        </Button>
                        <Button
                            id={`${idPrefix}-cancel`}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={dialog.saving}
                            onClick={onDismiss}>
                            Cancel
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default SubscriptionTrialDialog;
