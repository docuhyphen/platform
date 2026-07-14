import {ChangeEvent, KeyboardEvent, useState} from "react";
import {
    Button,
    Field,
    Input,
    InputOnChangeData,
    mergeClasses,
    MessageBar,
    MessageBarBody,
    Spinner,
} from "@fluentui/react-components";
import {MfaMethod} from "../../../models/models.tsx";
import AlternativeMfaMethodDialog from "./alternative-method-dialog/AlternativeMfaMethodDialog.tsx";
import {useSignInMfaStepStyles} from "./SignInMfaStepStyles.tsx";
interface SignInMfaStepProps
{
    message: string;
    method: MfaMethod;
    emailFallbackEnabled: boolean;
    code: string;
    successMessage: string;
    sessionExpired: boolean;
    busy: boolean;
    resending: boolean;
    resendCooldownRemaining: number;
    buttonWithLoadingClassName: string;
    onCodeChange: (event: ChangeEvent<HTMLInputElement>, data: InputOnChangeData) => void;
    onCodeKeyDown: (event: KeyboardEvent<HTMLInputElement>) => void;
    onResend: () => void;
    onUseEmailFallback: () => void;
    onVerify: () => void;
    onStartOver: () => void;
}

const SignInMfaStep = ({
    message,
    method,
    emailFallbackEnabled,
    code,
    successMessage,
    sessionExpired,
    busy,
    resending,
    resendCooldownRemaining,
    buttonWithLoadingClassName,
    onCodeChange,
    onCodeKeyDown,
    onResend,
    onUseEmailFallback,
    onVerify,
    onStartOver,
}: SignInMfaStepProps) =>
{
    const [alternativeMethodDialogOpen, setAlternativeMethodDialogOpen] = useState(false);
    const styles = useSignInMfaStepStyles();
    const chooseEmail = () =>
    {
        setAlternativeMethodDialogOpen(false);
        onUseEmailFallback();
    };

    return <div id={"sign-in-mfa-step"}>
    <span id={"sign-in-mfa-message"}>{message}</span>
    {sessionExpired && <MessageBar
        id={"sign-in-mfa-expired-message"}
        intent={"warning"}>
        <MessageBarBody id={"sign-in-mfa-expired-message-body"}>
            Your sign-in session has expired. Please sign in again.
        </MessageBarBody>
    </MessageBar>}
    <Field
        id={"sign-in-otp-field"}
        className={styles.codeField}
        label={"Verification code"}
        validationState={successMessage ? "success" : "none"}
        validationMessage={successMessage}
        hint={successMessage || message
            ? undefined
            : (method === 'EMAIL'
                ? "A verification code has been sent to your email"
                : `Enter the code from ${method === 'GOOGLE_AUTHENTICATOR' ? 'Google' : 'Microsoft'} Authenticator`)}>
        <Input
            id={"sign-in-otp-input"}
            value={code}
            maxLength={6}
            autoComplete={"one-time-code"}
            disabled={resending || busy || sessionExpired}
            onChange={onCodeChange}
            onKeyDown={onCodeKeyDown}
        />
    </Field>
    {!sessionExpired && <div
        id={"sign-in-mfa-action-row"}
        className={styles.actionRow}>
        {method === 'EMAIL' && <Button
            id={"sign-in-resend-otp-btn"}
            appearance={"transparent"}
            size={"small"}
            disabled={resending || busy || resendCooldownRemaining > 0}
            shape={"circular"}
            onClick={onResend}
            className={buttonWithLoadingClassName}>
            {resending && <Spinner
                id={"sign-in-resend-spinner"}
                size={"tiny"}
            />} {resendCooldownRemaining > 0
                ? `Resend verification code (${resendCooldownRemaining}s)`
                : "Resend verification code"}
        </Button>}
        {method !== 'EMAIL' && emailFallbackEnabled && <Button
            id={"sign-in-use-another-method-btn"}
            appearance={"transparent"}
            size={"small"}
            disabled={resending || busy}
            shape={"circular"}
            onClick={() => setAlternativeMethodDialogOpen(true)}>
            Use another method
        </Button>}
        <Button
            id={"sign-in-verify-code-btn"}
            onClick={onVerify}
            disabled={resending || busy}
            appearance={"primary"}
            className={mergeClasses(buttonWithLoadingClassName, styles.verifyButton)}
            shape={"circular"}>
            {busy && <Spinner
                id={"sign-in-verification-spinner"}
                size={"tiny"}
            />} {busy
                ? "Verifying code"
                : "Verify code"}
        </Button>
    </div>}
    {sessionExpired && <Button
        id={"sign-in-start-over-btn"}
        onClick={onStartOver}
        appearance={"primary"}
        shape={"circular"}>
        Start over
    </Button>}
    <AlternativeMfaMethodDialog
        open={alternativeMethodDialogOpen}
        busy={resending}
        onChooseEmail={chooseEmail}
        onClose={() => setAlternativeMethodDialogOpen(false)}
    />
</div>;
};

export default SignInMfaStep;
