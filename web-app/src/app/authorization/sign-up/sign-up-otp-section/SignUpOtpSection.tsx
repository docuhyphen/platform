import {KeyboardEvent} from "react";
import {Button, Field, Input, Spinner} from "@fluentui/react-components";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {SignUpState} from "../useSignUp.ts";

interface SignUpOtpSectionProps
{
    signUp: SignUpState;
}

const SignUpOtpSection = ({signUp}: SignUpOtpSectionProps) =>
{
    const globalStyles = useGlobalStyles();
    const validationState = signUp.otpRegenerationFailedMsg
        ? "error"
        : (signUp.otpRegenerationSuccessfulMsg ? "success" : "none");

    const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) =>
    {
        if (event.key === 'Enter')
        {
            void signUp.onCompleteSignUp();
        }
    };

    return (
        <>
            <Field
                id={"sign-up-field-1"}
                label={"Verification code"}
                validationState={validationState}
                validationMessage={signUp.otpRegenerationFailedMsg || signUp.otpRegenerationSuccessfulMsg}>
                <Input
                    id={"sign-up-otp-input"}
                    type="text"
                    name="otp"
                    maxLength={6}
                    value={signUp.formData.otp}
                    autoComplete="false"
                    onChange={signUp.handleChange}
                    onKeyDown={onKeyDown}
                />
            </Field>
            <Button
                id={"sign-up-resend-otp-btn"}
                onClick={signUp.onRegenerateOTP}
                size={"small"}
                disabled={signUp.completingSignUp}
                appearance={"transparent"}
                shape={"circular"}
                className={globalStyles.buttonWithLoading}>
                {signUp.regeneratingOtp && <Spinner id={"sign-up-spinner-1"}
                                                    size={"tiny"}/>}
                Resend verification code
            </Button>
        </>
    );
};

export default SignUpOtpSection;
