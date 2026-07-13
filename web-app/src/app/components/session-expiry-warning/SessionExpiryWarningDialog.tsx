import React from "react";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Spinner,
    Text,
} from "@fluentui/react-components";
import {useSessionExpiryWarningDialogStyles} from "./SessionExpiryWarningDialogStyles.tsx";

interface SessionExpiryWarningDialogProps
{
    isOpen: boolean;
    secondsRemaining: number;
    isContinuing: boolean;
    errorMessage: string | null;
    onContinue: () => void;
    onSignOut: () => void;
}

const SessionExpiryWarningDialog: React.FC<SessionExpiryWarningDialogProps> = (
    {
        isOpen,
        secondsRemaining,
        isContinuing,
        errorMessage,
        onContinue,
        onSignOut,
    }
) =>
{
    const styles = useSessionExpiryWarningDialogStyles();
    const remainingLabel = `${secondsRemaining} second${secondsRemaining === 1 ? "" : "s"}`;

    return (
        <Dialog
            open={isOpen}
            modalType={"alert"}>
            <DialogSurface id={"session-expiry-warning-dialog"}>
                <DialogBody id={"session-expiry-warning-dialog-body"}>
                    <DialogTitle id={"session-expiry-warning-dialog-title"}>
                        Your session is about to expire
                    </DialogTitle>
                    <DialogContent
                        id={"session-expiry-warning-dialog-content"}
                        className={styles.content}>
                        <Text id={"session-expiry-warning-message"}>
                            You will be signed out due to inactivity unless you continue your session.
                        </Text>
                        <Text
                            id={"session-expiry-warning-countdown"}
                            className={styles.countdown}
                            weight={"semibold"}>
                            Signing out in {remainingLabel}.
                        </Text>
                        {errorMessage && (
                            <Text id={"session-expiry-warning-error"}>
                                {errorMessage}
                            </Text>
                        )}
                    </DialogContent>
                    <DialogActions id={"session-expiry-warning-actions"}>
                        <Button
                            id={"session-expiry-warning-continue"}
                            appearance={"primary"}
                            shape={"circular"}
                            disabled={isContinuing}
                            onClick={onContinue}>
                            {isContinuing && <Spinner size={"tiny"}/>}
                            Continue session
                        </Button>
                        <Button
                            id={"session-expiry-warning-sign-out"}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={isContinuing}
                            onClick={onSignOut}>
                            Sign out
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default SessionExpiryWarningDialog;
