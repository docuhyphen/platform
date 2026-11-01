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
    Field,
    Text,
    Textarea
} from "@fluentui/react-components";
import {useNoAuthExchangeDeclineDialogStyles} from "./NoAuthExchangeDeclineDialogStyles.tsx";
import NoAuthExchangeDecisionOtpHeader from "../decision-otp-header/NoAuthExchangeDecisionOtpHeader.tsx";
import {MIN_DECLINE_REASON_LENGTH, NoAuthExchangeDecisionOtpInputProps} from "../noAuthExchangeDecision.ts";

interface NoAuthExchangeDeclineDialogProps
{
    open: boolean;
    declineReason: string;
    onDeclineReasonChange: (value: string) => void;
    isDeclining: boolean;
    canConfirm: boolean;
    onConfirm: () => void;
    onCancel: () => void;
    otpInput: NoAuthExchangeDecisionOtpInputProps;
}

const NoAuthExchangeDeclineDialog: React.FC<NoAuthExchangeDeclineDialogProps> = (
    {
        open,
        declineReason,
        onDeclineReasonChange,
        isDeclining,
        canConfirm,
        onConfirm,
        onCancel,
        otpInput
    }) =>
{
    const styles = useNoAuthExchangeDeclineDialogStyles();

    return (
        <Dialog open={open}
                modalType={"alert"}>
            <DialogSurface id={"no-auth-exchange-user-decision-dialog-surface-1"}>
                <DialogBody id={"no-auth-exchange-user-decision-dialog-body-1"}>
                    <DialogTitle id={"no-auth-exchange-user-decision-dialog-title-1"}>Declining</DialogTitle>
                    <DialogContent id={"no-auth-exchange-user-decision-dialog-content-1"}
                                   className={styles.declineDialogContent}>
                        <NoAuthExchangeDecisionOtpHeader actionLabel={'decline'}
                                                         onSubmit={onConfirm}
                                                         {...otpInput}/>

                        <Field id={"no-auth-exchange-user-decision-field-2"}>
                            <Textarea id={"no-auth-exchange-decline-reason-textarea"}
                                      placeholder={"Reason for declining"}
                                      value={declineReason}
                                      onChange={(e) => onDeclineReasonChange(e.target.value)}
                                      maxLength={100}
                                      minLength={10}/>
                        </Field>
                        <Text id={"no-auth-exchange-user-decision-text-7"}
                              size={200}
                              className={styles.helperText}>
                            {declineReason.trim().length}/{MIN_DECLINE_REASON_LENGTH} minimum characters
                        </Text>
                    </DialogContent>
                </DialogBody>
                <DialogActions id={"no-auth-exchange-user-decision-dialog-actions-1"}>
                    <DialogTrigger>
                        <Button id={"no-auth-exchange-decline-confirm-btn"}
                                onClick={onConfirm}
                                disabled={isDeclining || !canConfirm}
                                shape={"circular"}
                                appearance={"primary"}>
                            Decline
                        </Button>
                    </DialogTrigger>
                    <Button id={"no-auth-exchange-decline-cancel-btn"}
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

export default NoAuthExchangeDeclineDialog;
