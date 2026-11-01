import {KeyboardEvent} from "react";
import {Button, Caption1, Field, Input, Link, Spinner, Subtitle1, Text} from "@fluentui/react-components";
import {ArrowLeftRegular} from "@fluentui/react-icons";
import {useNavigate} from "react-router-dom";
import {useAccountRecoveryStyles} from "../AccountRecoveryStyles.tsx";
import {useAuthorizationStyles} from "../../AuthorizationStyles.tsx";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {AccountRecoveryState} from "../useAccountRecovery.ts";
import AccountRecoveryErrorMessage from "../account-recovery-error-message/AccountRecoveryErrorMessage.tsx";
import AccountRecoveryOtpSection from "../account-recovery-otp-section/AccountRecoveryOtpSection.tsx";
import AccountRecoveryPasswordFields from "../account-recovery-password-fields/AccountRecoveryPasswordFields.tsx";

interface AccountRecoveryFormProps
{
    recovery: AccountRecoveryState;
}

const AccountRecoveryForm = ({recovery}: AccountRecoveryFormProps) =>
{
    const navigate = useNavigate();
    const accountRecoveryStyles = useAccountRecoveryStyles();
    const authorizationStyles = useAuthorizationStyles();
    const globalStyles = useGlobalStyles();

    const onEmailKeyDown = (event: KeyboardEvent<HTMLInputElement>) =>
    {
        if (event.key === 'Enter')
        {
            void recovery.onInitiatePasswordReset();
        }
    };

    return (
        <div id={"account-recovery-div-4"}
             className={authorizationStyles.authorizationFormSection}>
            <Subtitle1 id={"account-recovery-subtitle1-1"}
                       align={"center"}>
                <Button
                    id={"account-recovery-back-btn"}
                    shape={"circular"}
                    icon={<ArrowLeftRegular/>}
                    appearance={"transparent"}
                    onClick={() => navigate("/sign-in")}/>
                Recover account
            </Subtitle1>

            <AccountRecoveryErrorMessage message={recovery.responseErrorMessage}
                                         onDismiss={recovery.onDismissError}/>

            <Field
                id={"account-recovery-field-4"}
                label={"Email"}
                validationState={recovery.pwdResetSuccessfulMsg ? "success" : "none"}
                validationMessage={recovery.pwdResetSuccessfulMsg}>
                <Input
                    id={"account-recovery-email-input"}
                    type="email"
                    name="email"
                    maxLength={254}
                    disabled={recovery.initiatingPwdReset}
                    autoComplete={"false"}
                    value={recovery.formData.email}
                    onChange={recovery.handleChange}
                    onKeyDown={onEmailKeyDown}
                />
            </Field>

            {recovery.pwdResetInitiationSuccessful &&
                <div id={"account-recovery-div-5"}
                     className={accountRecoveryStyles.signUpCompletionForm}>
                    <AccountRecoveryOtpSection recovery={recovery}/>
                    <AccountRecoveryPasswordFields recovery={recovery}/>
                    <Button
                        id={"account-recovery-reset-btn"}
                        onClick={recovery.onCompletePasswordReset}
                        appearance={"primary"}
                        shape={"circular"}
                        disabled={recovery.regeneratingOtp}
                        className={globalStyles.buttonWithLoading}>
                        {recovery.completingPwdReset && <Spinner id={"account-recovery-spinner-2"}
                                                                 size={"tiny"}/>}
                        {recovery.completingPwdReset ? "Resetting password" : "Reset Password"}
                    </Button>
                </div>
            }

            {!recovery.pwdResetInitiationSuccessful &&
                <Button
                    id={"account-recovery-initiate-btn"}
                    onClick={recovery.onInitiatePasswordReset}
                    appearance={"primary"}
                    shape={"circular"}
                    className={globalStyles.buttonWithLoading}>
                    {recovery.initiatingPwdReset && <Spinner id={"account-recovery-spinner-3"}
                                                             size={"tiny"}/>}
                    Recover
                </Button>
            }
            <div id={"account-recovery-div-6"}
                 className={accountRecoveryStyles.authHasAccount}>
                <Caption1 id={"account-recovery-caption1-1"}> Don't have an account? &nbsp;
                    <Link id={"account-recovery-link-1"}
                          onClick={() => navigate("/sign-up")}
                          disabled={recovery.initiatingPwdReset || recovery.completingPwdReset}>
                        <Text id={"account-recovery-text-4"}
                              weight="semibold">Sign up</Text>
                    </Link>
                </Caption1>
            </div>
        </div>
    );
};

export default AccountRecoveryForm;
