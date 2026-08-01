import {useEffect, useState} from "react";
import {StepUpMfaType, StepUpPrompt, subscribeStepUp} from "../../../services/stepUpBroker";
import {getOtpFriendlyMessage, normalizeApiError} from "../../../utils/apiErrorUtils";
import {useResendCooldownError} from "./useResendCooldownError.ts";

export const useStepUpModalController = () =>
{
    const [prompt, setPrompt] = useState<StepUpPrompt | null>(null);
    const [mfaTypeOverride, setMfaTypeOverride] = useState<StepUpMfaType | null>(null);
    const [otp, setOtp] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [resending, setResending] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [info, setInfo] = useState<string | null>(null);
    const {
        cooldownMessage,
        cooldownRemaining,
        startResendCooldown,
        clearResendCooldown,
    } = useResendCooldownError();

    useEffect(() => subscribeStepUp((p) =>
    {
        setPrompt(p);
        setMfaTypeOverride(null);
        clearResendCooldown();
        if (!p)
        {
            setOtp("");
            setError(null);
            setInfo(null);
            setSubmitting(false);
            setResending(false);
        }
    }), [clearResendCooldown]);

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
        clearResendCooldown();
        try
        {
            const result = await prompt.resendOtp();
            if (result?.mfaType)
            {
                setMfaTypeOverride(result.mfaType);
            }
            setInfo(result?.message || "A new verification code has been sent.");
        }
        catch (e: unknown)
        {
            const normalized = normalizeApiError(e, "Could not resend code.");
            if (normalized.reasonCode === "OTP_RATE_LIMITED")
            {
                startResendCooldown(normalized);
                if (prompt.mfaType !== "EMAIL" && prompt.emailFallbackEnabled)
                {
                    setMfaTypeOverride("EMAIL");
                }
                return;
            }
            setError(getOtpFriendlyMessage(normalized));
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

    const effectiveMfaType = mfaTypeOverride ?? prompt?.mfaType;
    const isEmailFallback = prompt?.mfaType !== "EMAIL" && effectiveMfaType === "EMAIL";

    return {
        prompt,
        isOtpFlow: prompt?.method === "INTERNAL_EMAIL_OTP",
        otp,
        setOtp,
        submitting,
        resending,
        info,
        effectiveMfaType,
        verificationMessage: isEmailFallback ? "Enter the email verification code." : prompt?.message,
        displayError: error ?? cooldownMessage,
        resendDisabled: submitting || cooldownRemaining > 0,
        onSubmitOtp,
        onResendOtp,
        onContinueExternal,
        onCancel,
    };
};
