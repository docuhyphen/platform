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
    Text,
} from "@fluentui/react-components";
import SubscriptionTrialTransitionFields from "./SubscriptionTrialTransitionFields.tsx";
import {useSubscriptionTrialTransitionDialogStyles} from "./SubscriptionTrialTransitionDialogStyles.tsx";
import {
    SubscriptionTrialTransitionMode,
    useSubscriptionTrialTransitionDialog,
} from "./useSubscriptionTrialTransitionDialog.ts";

interface Props
{
    open: boolean;
    ownerName: string;
    ownerKind: "user" | "organization";
    mode: SubscriptionTrialTransitionMode;
    defaultSeatCapacity: number | null;
    onDismiss: () => void;
    onSaved: () => void;
    onEnd: (reason: string) => Promise<unknown>;
    onConvert: (
        billingFrequency: "MONTHLY" | "ANNUAL",
        currentPeriodEnd: string,
        seatCapacity: number | null,
        reason: string,
    ) => Promise<unknown>;
}

const SubscriptionTrialTransitionDialog = (props: Props) =>
{
    const styles = useSubscriptionTrialTransitionDialogStyles();
    const dialog = useSubscriptionTrialTransitionDialog(props);
    const idPrefix = `platform-${props.ownerKind}-trial-${props.mode.toLowerCase()}`;
    const consequence = props.ownerKind === "user"
        ? "Ending this trial immediately moves the user to the Free plan. Existing data remains readable."
        : "Ending this trial immediately makes Business mutations read-only. Existing data and Business configuration remain readable.";
    return (
        <Dialog
            open={props.open}
            onOpenChange={(_, data) => !data.open && !dialog.saving && props.onDismiss()}>
            <DialogSurface id={`${idPrefix}-surface`}>
                <DialogBody
                    id={`${idPrefix}-body`}
                    className={styles.dialogBody}>
                    <DialogTitle id={`${idPrefix}-title`}>
                        {dialog.actionLabel} for {props.ownerName}
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
                        {props.mode === "END" && (
                            <Text
                                id={`${idPrefix}-consequence`}
                                className={styles.consequence}>
                                {consequence}
                            </Text>
                        )}
                        <SubscriptionTrialTransitionFields
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
                            onClick={props.onDismiss}>
                            Cancel
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default SubscriptionTrialTransitionDialog;
