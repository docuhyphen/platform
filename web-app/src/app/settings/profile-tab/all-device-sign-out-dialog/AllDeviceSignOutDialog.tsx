import React, {useState} from "react";
import SignOutClickSurface from "../../../components/SignOutClickSurface.tsx";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Spinner,
    Text
} from "@fluentui/react-components";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {useAllDeviceSignOutDialogStyles} from "./AllDeviceSignOutDialogStyles.tsx";

interface AllDeviceSignOutDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
}

const AllDeviceSignOutDialog: React.FC<AllDeviceSignOutDialogProps> = (
    {
        isOpen,
        onDismiss
    }
) =>
{
    const [signingOut, setSigningOut] = useState(false);
    const globalStyles = useGlobalStyles()
    const styles = useAllDeviceSignOutDialogStyles()

    return <>
        <Dialog modalType="alert"
                   open={isOpen}>
        <DialogSurface id={"all-device-sign-out-dialog-dialog-surface-1"}>
            <DialogBody id={"all-device-sign-out-dialog-dialog-body-1"}>
                <DialogTitle id={"all-device-sign-out-dialog-dialog-title-1"}>Sign out of all devices</DialogTitle>
                <DialogContent id={"all-device-sign-out-dialog-dialog-content-1"}
                               className={styles.dialogContentContainer}>
                    <Text id={"all-device-sign-out-dialog-text-1"}>
                        Are you sure you want to sign out of all devices? This will sign you out from all devices
                        including this one.
                    </Text>
                </DialogContent>
            </DialogBody>
            <DialogActions id={"all-device-sign-out-dialog-dialog-actions-1"}>
                <Button id={"button-sign-out-all-devices"}
                        shape={"circular"}
                        appearance={"primary"}
                        disabled={signingOut}
                        className={globalStyles.buttonWithLoading}>
                    {signingOut && <Spinner id={"all-device-sign-out-dialog-spinner-1"}
                                            size={"tiny"}/>}
                    <SignOutClickSurface
                        outOfAllDevices={true}
                        onSignOut={() => setSigningOut(true)}/>
                </Button>
                <DialogTrigger disableButtonEnhancement>
                    <Button id={"button-all-device-sign-out-close"}
                            appearance="secondary"
                            shape={"circular"}
                            disabled={signingOut}
                            onClick={() => onDismiss()}>
                        Close
                    </Button>
                </DialogTrigger>
            </DialogActions>
        </DialogSurface>
    </Dialog>
    </>
}

export default AllDeviceSignOutDialog