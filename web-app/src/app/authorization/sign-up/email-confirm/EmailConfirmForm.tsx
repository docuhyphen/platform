import {KeyboardEvent} from "react";
import {Button, Caption1, Field, Link, Spinner, Subtitle1, Text} from "@fluentui/react-components";
import {ArrowLeftRegular} from "@fluentui/react-icons";
import {useNavigate} from "react-router-dom";
import {useSignUpStyles} from "../SignUpStyles.tsx";
import {useAuthorizationStyles} from "../../AuthorizationStyles.tsx";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import EmailConfirmErrorBar from "./EmailConfirmErrorBar.tsx";
import EmailConfirmPasswordFields from "./EmailConfirmPasswordFields.tsx";
import {SignUpEmailConfirmState} from "./useSignUpEmailConfirm.ts";

interface EmailConfirmFormProps
{
    confirmation: SignUpEmailConfirmState;
}

const EmailConfirmForm = ({confirmation}: EmailConfirmFormProps) =>
{
    const navigate = useNavigate();
    const signUpStyles = useSignUpStyles();
    const authorizationStyles = useAuthorizationStyles();
    const globalStyles = useGlobalStyles();
    const busy = confirmation.completingSignUp || confirmation.regeneratingOtp;
    const resendValidationState = confirmation.otpRegenerationFailedMsg
        ? "error"
        : (confirmation.otpRegenerationSuccessMsg ? "success" : "none");

    const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) =>
    {
        if (event.key === 'Enter')
        {
            void confirmation.onCompleteSignUp();
        }
    };

    return <div
        id={"email-confirm-form"}
        className={authorizationStyles.authorizationFormSection}>
        <Subtitle1
            id={"email-confirm-form-title"}
            align={"center"}>
            <Button
                id={"email-confirm-form-back-btn"}
                shape={"circular"}
                icon={<ArrowLeftRegular/>}
                appearance={"transparent"}
                onClick={() => navigate("/sign-up")}
            />
            Finish creating your account
        </Subtitle1>
        <Text
            id={"email-confirm-form-intro"}
            size={300}
            align={"center"}>
            Verifying <strong id={"email-confirm-form-strong-1"}>{confirmation.email}</strong>. Choose a password to complete sign up.
        </Text>
        <EmailConfirmErrorBar
            message={confirmation.errorMessage}
            onDismiss={confirmation.onDismissError}
        />
        <div
            id={"email-confirm-password-fields"}
            className={signUpStyles.signUpCompletionForm}>
            <EmailConfirmPasswordFields
                password={confirmation.password}
                confirmationPassword={confirmation.confirmationPassword}
                disabled={busy}
                onChange={confirmation.onPasswordChange}
                onKeyDown={onKeyDown}
            />
            <Button
                id={"email-confirm-complete-btn"}
                onClick={() => { void confirmation.onCompleteSignUp(); }}
                appearance={"primary"}
                shape={"circular"}
                disabled={busy}
                className={globalStyles.buttonWithLoading}>
                {confirmation.completingSignUp && <Spinner
                    id={"email-confirm-complete-spinner"}
                    size={"tiny"}
                />}
                {confirmation.completingSignUp ? "Completing sign up" : "Complete sign up"}
            </Button>
        </div>
        <Field
            id={"email-confirm-resend-field"}
            validationState={resendValidationState}
            validationMessage={confirmation.otpRegenerationFailedMsg || confirmation.otpRegenerationSuccessMsg}>
            <Button
                id={"email-confirm-resend-btn"}
                onClick={() => { void confirmation.onResendCode(); }}
                size={"small"}
                disabled={busy}
                appearance={"transparent"}
                shape={"circular"}
                className={globalStyles.buttonWithLoading}>
                {confirmation.regeneratingOtp && <Spinner
                    id={"email-confirm-resend-spinner"}
                    size={"tiny"}
                />}
                Send me a new verification link
            </Button>
        </Field>
        <div
            id={"email-confirm-sign-in-prompt"}
            className={signUpStyles.authHasAccount}>
            <Caption1 id={"email-confirm-sign-in-caption"}>
                Already have an account? &nbsp;
                <Link
                    id={"email-confirm-sign-in-link"}
                    onClick={() => navigate("/sign-in")}
                    disabled={confirmation.completingSignUp}>
                    <Text id={"email-confirm-form-text-1"}
                          weight="semibold">Sign in</Text>
                </Link>
            </Caption1>
        </div>
    </div>;
};

export default EmailConfirmForm;
