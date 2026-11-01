import React from "react";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger
} from "@fluentui/react-components";
import {useNoAuthExchangeAcceptDialogStyles} from "./NoAuthExchangeAcceptDialogStyles.tsx";
import NoAuthExchangeDecisionOtpHeader from "../decision-otp-header/NoAuthExchangeDecisionOtpHeader.tsx";
import {NoAuthExchangeDecisionOtpInputProps} from "../noAuthExchangeDecision.ts";

interface NoAuthExchangeAcceptDialogProps
{
    open: boolean;
    isAccepting: boolean;
    canConfirm: boolean;
    onConfirm: () => void;
    onCancel: () => void;
    otpInput: NoAuthExchangeDecisionOtpInputProps;
}

const NoAuthExchangeAcceptDialog: React.FC<NoAuthExchangeAcceptDialogProps> = (
    {
        open,
        isAccepting,
        canConfirm,
        onConfirm,
        onCancel,
        otpInput
    }) =>
{
    const styles = useNoAuthExchangeAcceptDialogStyles();

    return (
        <Dialog open={open}>
            <DialogSurface id={"no-auth-exchange-user-decision-dialog-surface-2"}>
                <DialogBody id={"no-auth-exchange-user-decision-dialog-body-2"}>
                    <DialogTitle id={"no-auth-exchange-user-decision-dialog-title-2"}>Accepting</DialogTitle>
                    <DialogContent id={"no-auth-exchange-user-decision-dialog-content-2"}
                                   className={styles.acceptDialogContent}>
                        <NoAuthExchangeDecisionOtpHeader actionLabel={'accept'}
                                                         onSubmit={onConfirm}
                                                         {...otpInput}/>
                    </DialogContent>
                </DialogBody>
                <DialogActions id={"no-auth-exchange-user-decision-dialog-actions-2"}>
                    <DialogTrigger>
                        <Button id={"no-auth-exchange-accept-confirm-btn"}
                                onClick={onConfirm}
                                disabled={isAccepting || !canConfirm}
                                shape={"circular"}
                                appearance={"primary"}>
                            Accept
                        </Button>
                    </DialogTrigger>
                    <Button id={"no-auth-exchange-accept-cancel-btn"}
                            onClick={onCancel}
                            shape={"circular"}
                            appearance={"secondary"}>
                        Cancel
                    </Button>
                </DialogActions>
            </DialogSurface>
        </Dialog>
    );
};

export default NoAuthExchangeAcceptDialog;
