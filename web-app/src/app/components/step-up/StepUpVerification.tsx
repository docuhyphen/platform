import React from "react";
import {Button, Field, Input, Spinner, Text} from "@fluentui/react-components";
import {RegenerateOTPIcon} from "../IconBundles.tsx";
import {useStepUpVerificationStyles} from "./StepUpVerificationStyles.tsx";
import {identityProviderDisplayName} from "../../authorization/identityProviderDisplayName.ts";

export const STEP_UP_DIALOG_TITLE = "Confirm it's you";

export type StepUpVerificationMethod = "INTERNAL_EMAIL_OTP" | "EXTERNAL_RELOGIN";

interface StepUpVerificationProps
{
    method: StepUpVerificationMethod;
    /** The clause completing "...to {actionLabel}" / "To {actionLabel}, you must re-authenticate...". */
    actionLabel: React.ReactNode;
    provider?: string | null;
    error?: string | null;
    message?: string | null;

    // Internal verification-code fields only
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
    resendLabel?: string;
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
        message,
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
        resendLabel = "Resend code",
    }) =>
{
    const styles = useStepUpVerificationStyles();

    if (method !== "INTERNAL_EMAIL_OTP")
    {
        return (
            <div
                id={"step-up-external-verification"}
                className={styles.container}>
                <Text id={"step-up-external-instructions"}>
                    To {actionLabel}, you must re-authenticate with {identityProviderDisplayName(provider)}.
                    Silent SSO is disabled for this step.
                </Text>
                {error && <Text
                    id={"step-up-external-error"}
                    className={styles.errorText}>
                    {error}
                </Text>}
            </div>
        );
    }

    return (
        <div
            id={"step-up-code-verification"}
            className={styles.container}>
            <div id={"step-up-code-instructions"}>
                <Text id={"step-up-code-instructions-copy"}>
                    {message || "For your security, enter your verification code."} To
                </Text>
                <Text
                    id={"step-up-code-action"}
                    weight={"semibold"}>
                    {" "} {actionLabel}.
                </Text>
            </div>
            <Field
                id={"step-up-code-field"}
                label={"Verification code"}
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
            {info && <Text
                id={"step-up-code-info"}
                className={styles.infoText}>
                {info}
            </Text>}
            {onResend && <div
                id={"step-up-resend-row"}
                className={styles.resendRow}>
                <Button
                    id={resendButtonId}
                    appearance="secondary"
                    shape="circular"
                    icon={<RegenerateOTPIcon/>}
                    disabled={resendDisabled || resending}
                    onClick={onResend}
                >
                    {resending && <Spinner
                        id={"step-up-resend-spinner"}
                        size={"tiny"}
                    />}
                    {resendLabel}
                </Button>
            </div>}
        </div>
    );
};

export default StepUpVerification;
