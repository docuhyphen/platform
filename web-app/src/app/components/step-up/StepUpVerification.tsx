import React from "react";
import {Button, Field, Input, Spinner, Text} from "@fluentui/react-components";
import {RegenerateOTPIcon} from "../IconBundles.tsx";
import {useStepUpVerificationStyles} from "./StepUpVerificationStyles.tsx";

export const STEP_UP_DIALOG_TITLE = "Confirm it's you";

export type StepUpVerificationMethod = "INTERNAL_EMAIL_OTP" | "EXTERNAL_RELOGIN";

interface StepUpVerificationProps
{
    method: StepUpVerificationMethod;
    /** The clause completing "...to {actionLabel}" / "To {actionLabel}, you must re-authenticate...". */
    actionLabel: React.ReactNode;
    provider?: string | null;
    error?: string | null;

    // INTERNAL_EMAIL_OTP fields only
    otp?: string;
    onOtpChange?: (value: string) => void;
    onSubmitOtp?: () => void;
    submitting?: boolean;
    info?: string | null;
    onResend?: () => void;
    resending?: boolean;
    resendDisabled?: boolean;
    otpInputId?: string;
    resendButtonId?: string;
}

/**
 * Shared body content (message + code input + resend, or external re-login message) for every
 * step-up re-authentication dialog. Callers own the surrounding Dialog/DialogTitle/DialogActions
 * so they can tailor actions (e.g. "Back to review" vs "Cancel"), but must render this for the
 * verification body rather than re-implementing the OTP/resend UI.
 */
const StepUpVerification: React.FC<StepUpVerificationProps> = (
    {
        method,
        actionLabel,
        provider,
        error,
        otp = "",
        onOtpChange,
        onSubmitOtp,
        submitting = false,
        info,
        onResend,
        resending = false,
        resendDisabled = false,
        otpInputId = "step-up-otp-input",
        resendButtonId = "step-up-resend-btn",
    }) =>
{
    const styles = useStepUpVerificationStyles();

    if (method !== "INTERNAL_EMAIL_OTP")
    {
        return (
            <div className={styles.container}>
                <Text>
                    To {actionLabel}, you must re-authenticate with {provider || "your identity provider"}.
                    Silent SSO is disabled for this step.
                </Text>
                {error && <Text className={styles.errorText}>{error}</Text>}
            </div>
        );
    }

    return (
        <div className={styles.container}>
            <div>
                <Text>
                    For your security, enter the verification code sent to your email to
                </Text>
                <Text weight={"semibold"}> {actionLabel}. </Text>
            </div>
            <Field
                label="Verification code"
                validationState={error ? "error" : "none"}
                validationMessage={error ?? undefined}
            >
                <Input
                    id={otpInputId}
                    type="text"
                    autoComplete="one-time-code"
                    value={otp}
                    disabled={submitting}
                    onChange={(e) => onOtpChange?.(e.target.value)}
                    onKeyDown={(e) => { if (e.key === "Enter") onSubmitOtp?.(); }}
                />
            </Field>
            {info && <Text className={styles.infoText}>{info}</Text>}
            <div className={styles.resendRow}>
                <Button
                    id={resendButtonId}
                    appearance="secondary"
                    shape="circular"
                    icon={<RegenerateOTPIcon/>}
                    disabled={resendDisabled || resending}
                    onClick={onResend}
                >
                    {resending && <Spinner size="tiny"/>}
                    Resend code
                </Button>
            </div>
        </div>
    );
};

export default StepUpVerification;
