import React, {useEffect, useState} from "react";
import {
    Dialog,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
} from "@fluentui/react-components";
import {StepUpPrompt, subscribeStepUp} from "../../../services/stepUpBroker";
import {getOtpFriendlyMessage, normalizeApiError} from "../../../utils/apiErrorUtils";
import StepUpVerification, {STEP_UP_DIALOG_TITLE} from "./StepUpVerification.tsx";
import StepUpDialogActions from "./StepUpDialogActions.tsx";
import {friendlyStepUpActionLabel} from "./stepUpLabels.ts";
const StepUpModal: React.FC = () =>
{
    const [prompt, setPrompt] = useState<StepUpPrompt | null>(null);
    const [otp, setOtp] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [resending, setResending] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [info, setInfo] = useState<string | null>(null);
    useEffect(() =>
    {
        return subscribeStepUp((p) =>
        {
            setPrompt(p);
            if (!p)
            {
                setOtp("");
                setError(null);
                setInfo(null);
                setSubmitting(false);
                setResending(false);
            }
        });
    }, []);
    const onSubmitOtp = async () =>
    {
        if (!prompt?.submitOtp || submitting) return;
        if (!otp.trim())
        {
            setError("Verification code is required");
            return;
        }

        setSubmitting(true);
        setError(null);
        setInfo(null);
        try
        {
            const ok = await prompt.submitOtp(otp.trim());
            if (!ok)
            {
                setError("Verification failed. Please try again.");
            }
        }
        catch (e: unknown)
        {
            setError(getOtpFriendlyMessage(normalizeApiError(e, "Verification failed.")));
        }
        finally
        {
            setSubmitting(false);
        }
    };
    const onResendOtp = async () =>
    {
        if (!prompt?.resendOtp || resending) return;
        setResending(true);
        setError(null);
        try
        {
            const message = await prompt.resendOtp();
            setInfo(message);
        }
        catch (e: unknown)
        {
            setError(getOtpFriendlyMessage(normalizeApiError(e, "Could not resend code.")));
        }
        finally
        {
            setResending(false);
        }
    };
    const onContinueExternal = async () =>
    {
        if (!prompt?.continueExternal || submitting) return;
        setSubmitting(true);
        setError(null);
        try
        {
            await prompt.continueExternal();
        }
        catch (e: unknown)
        {
            setError(getOtpFriendlyMessage(normalizeApiError(e, "Could not start external re-login.")));
            setSubmitting(false);
        }
    };
    const onCancel = () =>
    {
        if (submitting) return;
        prompt?.cancel();
    };
    const isOtpFlow = prompt?.method === 'INTERNAL_EMAIL_OTP';
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
                            message={prompt?.message}
                            error={error}
                            otp={otp}
                            onOtpChange={setOtp}
                            onSubmitOtp={onSubmitOtp}
                            submitting={submitting}
                            info={info}
                            onResend={onResendOtp}
                            resending={resending}
                            resendDisabled={submitting}
                            otpInputId="step-up-otp-input"
                            resendButtonId="step-up-resend-btn"
                            resendLabel={prompt?.mfaType === 'EMAIL' ? 'Resend code' : 'Use email fallback'}
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
