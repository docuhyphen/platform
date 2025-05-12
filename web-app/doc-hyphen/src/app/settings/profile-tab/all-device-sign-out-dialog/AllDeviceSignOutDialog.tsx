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

    return <>
        <Dialog modalType="alert"
                   open={isOpen}>
        <DialogSurface>
            <DialogBody>
                <DialogTitle>Sign out of all devices</DialogTitle>
                <DialogContent>
                    <Text>
                        Are you sure you want to sign out of all devices? This will sign you out from all devices
                        including this one.
                    </Text>
                </DialogContent>
            </DialogBody>
            <DialogActions>
                <Button shape={"circular"}
                        appearance={"primary"}
                        disabled={signingOut}
                        className={globalStyles.buttonWithLoading}>
                    {signingOut && <Spinner size={"tiny"}/>}
                    <SignOutClickSurface
                        onSignOut={() => setSigningOut(true)}/>
                </Button>
                <DialogTrigger disableButtonEnhancement>
                    <Button appearance="secondary"
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