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
import type {SessionExpiryReason} from "./useSessionInactivity.ts";

interface SessionExpiryWarningDialogProps
{
    isOpen: boolean;
    expiryReason: SessionExpiryReason;
    secondsRemaining: number;
    isContinuing: boolean;
    errorMessage: string | null;
    onContinue: () => void;
    onSignOut: () => void;
}

const SessionExpiryWarningDialog: React.FC<SessionExpiryWarningDialogProps> = (
    {
        isOpen,
        expiryReason,
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
    const isInactivityWarning = expiryReason === "INACTIVITY_TIMEOUT";

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
                            {isInactivityWarning
                                ? "You will be signed out due to inactivity unless you continue your session."
                                : "Your session has reached its maximum duration. Save your work before signing in again."}
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
                        {isInactivityWarning && <Button
                            id={"session-expiry-warning-continue"}
                            appearance={"primary"}
                            shape={"circular"}
                            disabled={isContinuing}
                            onClick={onContinue}>
                            {isContinuing && <Spinner size={"tiny"}/>}
                            Continue session
                        </Button>}
                        <Button
                            id={"session-expiry-warning-sign-out"}
                            appearance={isInactivityWarning ? "secondary" : "primary"}
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
