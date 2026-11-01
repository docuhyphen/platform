import {KeyboardEvent} from "react";
import {Button, Field, Input, Spinner} from "@fluentui/react-components";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {AccountRecoveryState} from "../useAccountRecovery.ts";

interface AccountRecoveryOtpSectionProps
{
    recovery: AccountRecoveryState;
}

const AccountRecoveryOtpSection = ({recovery}: AccountRecoveryOtpSectionProps) =>
{
    const globalStyles = useGlobalStyles();
    const validationState = recovery.otpRegenerationFailedMsg
        ? "error"
        : (recovery.otpRegenerationSuccessfulMsg ? "success" : "none");

    const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) =>
    {
        if (event.key === 'Enter')
        {
            void recovery.onCompletePasswordReset();
        }
    };

    return (
        <>
            <Field
                id={"account-recovery-field-1"}
                label={"Verification code"}
                validationState={validationState}
                validationMessage={recovery.otpRegenerationFailedMsg || recovery.otpRegenerationSuccessfulMsg}>
                <Input
                    id={"account-recovery-otp-input"}
                    type="text"
                    name="otp"
                    maxLength={6}
                    value={recovery.formData.otp}
                    autoComplete="false"
                    onChange={recovery.handleChange}
                    onKeyDown={onKeyDown}
                />
            </Field>
            <Button
                id={"account-recovery-resend-otp-btn"}
                onClick={recovery.onRegenerateOTP}
                size={"small"}
                disabled={recovery.completingPwdReset}
                appearance={"transparent"}
                shape={"circular"}
                className={globalStyles.buttonWithLoading}>
                {recovery.regeneratingOtp && <Spinner id={"account-recovery-spinner-1"}
                                                      size={"tiny"}/>}
                Resend verification code
            </Button>
        </>
    );
};

export default AccountRecoveryOtpSection;
