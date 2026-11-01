import React from "react";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Text
} from "@fluentui/react-components";
import {usePasswordResetDialogStyles} from "./PasswordResetDialogStyles.tsx";

interface PasswordResetDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
}

const PasswordResetDialog : React.FC<PasswordResetDialogProps> = (
    {
        isOpen,
        onDismiss
    }
) =>
{
    const styles = usePasswordResetDialogStyles()

    return <>
        <Dialog modalType="alert"
                open={isOpen}>
            <DialogSurface id={"password-reset-dialog-dialog-surface-1"}>
                <DialogBody id={"password-reset-dialog-dialog-body-1"}>
                    <DialogTitle id={"password-reset-dialog-dialog-title-1"}>Reset password</DialogTitle>
                    <DialogContent id={"password-reset-dialog-dialog-content-1"}
                                   className={styles.dialogContentContainer}>
                        <Text id={"password-reset-dialog-text-1"}>
                            This feature is temporarily unavailable. To reset your password please sign out and reset
                            your password using the <strong id={"password-reset-dialog-strong-1"}>"Recover Account"</strong> link on the sign page.
                        </Text>
                    </DialogContent>
                </DialogBody>
                <DialogActions id={"password-reset-dialog-dialog-actions-1"}>
                    <DialogTrigger disableButtonEnhancement>
                        <Button id={"button-password-reset-close"}
                                appearance="secondary"
                                shape={"circular"}
                                onClick={() => onDismiss()}>
                            Close
                        </Button>
                    </DialogTrigger>
                </DialogActions>
            </DialogSurface>
        </Dialog>
    </>
}

export default PasswordResetDialog;



