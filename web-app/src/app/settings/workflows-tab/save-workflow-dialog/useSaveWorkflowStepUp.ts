import {useState} from "react";
import {
    completeStepUpWithOtp,
    initiateStepUp,
    regenerateStepUpOtp,
    StepUpInitiateResponse,
} from "../../../../services/authApi.ts";
import {getOtpFriendlyMessage, normalizeApiError} from "../../../../utils/apiErrorUtils.ts";
import {useResendCooldownError} from "../../../components/step-up/useResendCooldownError.ts";

export type SaveWorkflowPhase = 'review' | 'verify-otp' | 'verify-external';

const WORKFLOW_SAVE_ACTION = "WORKFLOW_DEFINITION_SAVE";

interface Options
{
    isEdit: boolean;
    onConfirm: () => Promise<void>;
}

export const useSaveWorkflowStepUp = ({isEdit, onConfirm}: Options) =>
{
    const [phase, setPhase] = useState<SaveWorkflowPhase>('review');
    const [stepUpSession, setStepUpSession] = useState<StepUpInitiateResponse | null>(null);
    const [initiating, setInitiating] = useState(false);
    const [otp, setOtp] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [resending, setResending] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [info, setInfo] = useState<string | null>(null);
    const {cooldownMessage, cooldownRemaining, startResendCooldown, clearResendCooldown} = useResendCooldownError();

    const reset = () =>
    {
        setPhase('review');
        setStepUpSession(null);
        setInitiating(false);
        setOtp("");
        setSubmitting(false);
        setResending(false);
        setError(null);
        setInfo(null);
        clearResendCooldown();
    };

    const proceedToVerify = async () =>
    {
        setInitiating(true);
        setError(null);
        clearResendCooldown();
        try
        {
            const returnTo = `${window.location.pathname}${window.location.search}`;
            const session = await initiateStepUp(returnTo, WORKFLOW_SAVE_ACTION);
            setStepUpSession(session);
            setPhase(session.method === 'INTERNAL_EMAIL_OTP' ? 'verify-otp' : 'verify-external');
        }
        catch (e: unknown)
        {
            setError(getOtpFriendlyMessage(normalizeApiError(e, "Could not initiate verification. Please try again.")));
        }
        finally
        {
            setInitiating(false);
        }
    };

    const submitOtp = async () =>
    {
        if (!stepUpSession?.mfaSessionId || submitting) return;
        if (!otp.trim())
        {
            setError("Verification code is required.");
            return;
        }
        setSubmitting(true);
        setError(null);
        setInfo(null);
        try
        {
            const result = await completeStepUpWithOtp(stepUpSession.mfaSessionId, otp.trim());
            if (!result?.fresh)
            {
                setError("Verification failed. Please try again.");
                return;
            }
            await onConfirm();
        }
        catch (e: unknown)
        {
            setError(getOtpFriendlyMessage(normalizeApiError(
                e,
                isEdit ? "Failed to update workflow." : "Failed to save workflow.",
            )));
        }
        finally
        {
            setSubmitting(false);
        }
    };

    const resendOtp = async () =>
    {
        if (!stepUpSession?.mfaSessionId || resending) return;
        setResending(true);
        setError(null);
        clearResendCooldown();
        try
        {
            const result = await regenerateStepUpOtp(stepUpSession.mfaSessionId);
            setStepUpSession((current) => current
                ? {
                    ...current,
                    message: result?.mfaType === 'EMAIL' && current.mfaType !== 'EMAIL'
                        ? "Enter the email verification code."
                        : current.message,
                    mfaType: result?.mfaType ?? current.mfaType,
                    emailFallbackEnabled: result?.emailFallbackEnabled ?? current.emailFallbackEnabled,
                }
                : current);
            setInfo(result?.message || "A new verification code has been sent.");
        }
        catch (e: unknown)
        {
            const normalized = normalizeApiError(e, "Could not resend code.");
            if (normalized.reasonCode === "OTP_RATE_LIMITED")
            {
                startResendCooldown(normalized);
                if (stepUpSession.mfaType !== 'EMAIL' && stepUpSession.emailFallbackEnabled)
                {
                    setStepUpSession((current) => current
                        ? {
                            ...current,
                            message: "Enter the email verification code.",
                            mfaType: 'EMAIL',
                        }
                        : current);
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

    const continueExternal = async () =>
    {
        if (!stepUpSession?.authorizeUrl || submitting) return;
        setSubmitting(true);
        setError(null);
        try
        {
            window.location.assign(stepUpSession.authorizeUrl);
        }
        catch (e: unknown)
        {
            setError(getOtpFriendlyMessage(normalizeApiError(e, "Could not start external re-login.")));
            setSubmitting(false);
        }
    };

    const returnToReview = () =>
    {
        setPhase('review');
        setError(null);
    };

    return {
        phase,
        stepUpSession,
        initiating,
        otp,
        setOtp,
        submitting,
        resending,
        error,
        info,
        cooldownMessage,
        cooldownRemaining,
        isBusy: submitting || initiating || resending,
        reset,
        proceedToVerify,
        submitOtp,
        resendOtp,
        continueExternal,
        returnToReview,
    };
};
