import React from "react";
import {
    Dialog,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
} from "@fluentui/react-components";
import StepUpVerification, {STEP_UP_DIALOG_TITLE} from "./StepUpVerification.tsx";
import StepUpDialogActions from "./StepUpDialogActions.tsx";
import {friendlyStepUpActionLabel} from "./stepUpLabels.ts";
import {useStepUpModalController} from "./useStepUpModalController.ts";

const StepUpModal: React.FC = () =>
{
    const {
        prompt,
        isOtpFlow,
        otp,
        setOtp,
        submitting,
        resending,
        info,
        effectiveMfaType,
        verificationMessage,
        displayError,
        resendDisabled,
        onSubmitOtp,
        onResendOtp,
        onContinueExternal,
        onCancel,
    } = useStepUpModalController();

    return (
        <Dialog
            modalType={"alert"}
            open={Boolean(prompt)}>
            <DialogSurface id={"step-up-dialog-surface"}>
                <DialogBody id={"step-up-dialog-body"}>
                    <DialogTitle id={"step-up-dialog-title"}>{STEP_UP_DIALOG_TITLE}</DialogTitle>
                    <DialogContent id={"step-up-dialog-content"}>
                        <StepUpVerification
                            method={isOtpFlow ? "INTERNAL_EMAIL_OTP" : "EXTERNAL_RELOGIN"}
                            actionLabel={<>continue with <b>{friendlyStepUpActionLabel(prompt?.action)}</b></>}
                            provider={prompt?.provider}
                            message={verificationMessage}
                            error={displayError}
                            otp={otp}
                            onOtpChange={setOtp}
                            onSubmitOtp={onSubmitOtp}
                            submitting={submitting}
                            info={info}
                            onResend={onResendOtp}
                            resending={resending}
                            resendDisabled={resendDisabled}
                            otpInputId="step-up-otp-input"
                            resendButtonId="step-up-resend-btn"
                            resendLabel={effectiveMfaType === "EMAIL" ? "Resend code" : "Use email fallback"}
                        />
                    </DialogContent>
                </DialogBody>
                <StepUpDialogActions
                    otpFlow={isOtpFlow}
                    submitting={submitting}
                    provider={prompt?.provider}
                    onSubmitOtp={onSubmitOtp}
                    onContinueExternal={onContinueExternal}
                    onCancel={onCancel}
                />
            </DialogSurface>
        </Dialog>
    );
};

export default StepUpModal;
