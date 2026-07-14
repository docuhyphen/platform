import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Radio,
    RadioGroup,
} from "@fluentui/react-components";
import {useAlternativeMfaMethodDialogStyles} from "./AlternativeMfaMethodDialogStyles.tsx";

interface AlternativeMfaMethodDialogProps
{
    open: boolean;
    busy: boolean;
    onChooseEmail: () => void;
    onClose: () => void;
}

const AlternativeMfaMethodDialog = ({
    open,
    busy,
    onChooseEmail,
    onClose,
}: AlternativeMfaMethodDialogProps) =>
{
    const styles = useAlternativeMfaMethodDialogStyles();

    return <Dialog
        open={open}
        onOpenChange={(_, data) => {
            if (!data.open && !busy) onClose();
        }}>
        <DialogSurface id={"sign-in-alternative-method-dialog-surface"}>
            <DialogBody id={"sign-in-alternative-method-dialog-body"}>
                <DialogTitle id={"sign-in-alternative-method-dialog-title"}>
                    Use another method
                </DialogTitle>
                <DialogContent id={"sign-in-alternative-method-dialog-content"}>
                    <RadioGroup
                        id={"sign-in-alternative-method-list"}
                        className={styles.methodList}
                        value={"EMAIL"}>
                        <Radio
                            id={"sign-in-alternative-method-email"}
                            value={"EMAIL"}
                            label={"Email verification code"}
                        />
                    </RadioGroup>
                </DialogContent>
                <DialogActions id={"sign-in-alternative-method-dialog-actions"}>
                    <Button
                        id={"sign-in-alternative-method-continue-btn"}
                        appearance={"primary"}
                        shape={"circular"}
                        disabled={busy}
                        onClick={onChooseEmail}>
                        Continue
                    </Button>
                    <Button
                        id={"sign-in-alternative-method-cancel-btn"}
                        appearance={"secondary"}
                        shape={"circular"}
                        disabled={busy}
                        onClick={onClose}>
                        Cancel
                    </Button>
                </DialogActions>
            </DialogBody>
        </DialogSurface>
    </Dialog>;
};

export default AlternativeMfaMethodDialog;
