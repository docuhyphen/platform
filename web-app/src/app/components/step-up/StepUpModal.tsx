import React, {useEffect, useState} from "react";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Field,
    Input,
    Spinner,
    Text,
} from "@fluentui/react-components";
import {StepUpPrompt, subscribeStepUp} from "../../../services/stepUpBroker";
import {getOtpFriendlyMessage, normalizeApiError} from "../../../utils/apiErrorUtils";
import {useStepUpModalStyles} from "./StepUpModalStyles.tsx";

const friendlyActionLabel = (action?: string | null): string =>
{
    if (!action) return "this action";
    const cleaned = action.replace(/^ORG_/, "").replace(/_/g, " ").toLowerCase();
    return cleaned.charAt(0).toUpperCase() + cleaned.slice(1);
};

const StepUpModal: React.FC = () =>
{
    const styles = useStepUpModalStyles();
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
        <Dialog modalType="alert" open={!!prompt}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Confirm it's you</DialogTitle>
                    <DialogContent className={styles.dialogContent}>
                        {isOtpFlow ? (
                            <>
                                <Text>
                                    For your security, enter the verification code sent to your email to continue with <b>{friendlyActionLabel(prompt?.action)}</b>.
                                </Text>
                                <Field
                                    label="Verification code"
                                    validationState={error ? "error" : "none"}
                                    validationMessage={error ?? undefined}
                                >
                                    <Input
                                        id={"step-up-otp-input"}
                                        type="text"
                                        autoComplete="one-time-code"
                                        value={otp}
                                        disabled={submitting}
                                        onChange={(e) => setOtp(e.target.value)}
                                        onKeyDown={(e) =>
                                        {
                                            if (e.key === "Enter") onSubmitOtp();
                                        }}
                                    />
                                </Field>
                                {info && <Text>{info}</Text>}
                            </>
                        ) : (
                            <>
                                <Text>
                                    To continue with <b>{friendlyActionLabel(prompt?.action)}</b>, you must re-authenticate with {prompt?.provider || "your identity provider"}.
                                    Silent SSO is disabled for this step.
                                </Text>
                                {error && <Text>{error}</Text>}
                            </>
                        )}
                    </DialogContent>
                </DialogBody>
                <DialogActions>
                    {isOtpFlow ? (
                        <>
                            <Button
                                id={"step-up-verify-btn"}
                                appearance="primary"
                                shape="circular"
                                disabled={submitting}
                                onClick={onSubmitOtp}
                            >
                                {submitting && <Spinner size="tiny"/>}
                                Verify & continue
                            </Button>
                            <Button
                                id={"step-up-resend-btn"}
                                appearance="secondary"
                                shape="circular"
                                disabled={resending || submitting}
                                onClick={onResendOtp}
                            >
                                {resending && <Spinner size="tiny"/>}
                                Resend code
                            </Button>
                        </>
                    ) : (
                        <Button
                            id={"step-up-continue-external-btn"}
                            appearance="primary"
                            shape="circular"
                            disabled={submitting}
                            onClick={onContinueExternal}
                        >
                            {submitting && <Spinner size="tiny"/>}
                            Continue to {prompt?.provider || "provider"}
                        </Button>
                    )}
                    <Button
                        id={"step-up-cancel-btn"}
                        appearance="secondary"
                        shape="circular"
                        disabled={submitting}
                        onClick={onCancel}
                    >
                        Cancel
                    </Button>
                </DialogActions>
            </DialogSurface>
        </Dialog>
    );
};

export default StepUpModal;
