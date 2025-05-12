import React from "react";
import {
    Button,
    Dialog,
    DialogActions, DialogBody, DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Spinner
} from "@fluentui/react-components";
import SignOutClickSurface from "../../../components/SignOutClickSurface.tsx";

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
                        {/*<Text>*/}
                        {/*</Text>*/}
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



