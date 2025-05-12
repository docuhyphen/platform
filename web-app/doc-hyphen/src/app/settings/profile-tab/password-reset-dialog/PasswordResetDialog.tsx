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
    return <>
        <Dialog modalType="alert"
                open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Reset password</DialogTitle>
                    <DialogContent>
                        <Text>
                            This feature is temporarily unavailable. To reset your password please sign out and reset
                            your password using the <strong>"Recover Account"</strong> link on the sign page.
                        </Text>
                    </DialogContent>
                </DialogBody>
                <DialogActions>

                    <DialogTrigger disableButtonEnhancement>
                        <Button appearance="secondary"
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



