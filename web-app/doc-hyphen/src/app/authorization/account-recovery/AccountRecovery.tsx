import React, {ChangeEvent, useState} from 'react';
import {completePasswordReset, initiatePasswordReset, regeneratePasswordResetOtp} from "../../../services/authApi.ts";
import {useNavigate} from "react-router-dom";
import {
    Button,
    Caption1,
    Field,
    InfoLabel,
    Input,
    InputOnChangeData,
    Link,
    MessageBar,
    MessageBarActions,
    MessageBarBody,
    Spinner,
    Subtitle1,
    Text
} from "@fluentui/react-components";
import {DismissRegular} from "@fluentui/react-icons";
import AppLogo from "../../components/app-logo/AppLogo.tsx";
import AccountRecoveryCarousel from "../carousel/AccountRecoveryCarousel.tsx";
import {useAccountRecoveryStyles} from "./AccountRecoveryStyles.tsx";
import {useAuthorizationStyles} from "../AuthorizationStyles.tsx";
import {useGlobalStyles} from "../../../GlobalStyles.tsx";
import {ResponseError} from "../../models/models.tsx";
import validator from 'validator';

const AccountRecovery: React.FC = () =>
{
    const accountRecoveryStyles = useAccountRecoveryStyles();
    const authorizationStyles = useAuthorizationStyles();
    const globalStyles = useGlobalStyles();
    const navigate = useNavigate();
    const [formData, setFormData] = useState({
        email: '',
        otp: '',
        password: '',
        confirmationPassword: ''
    });
    const [pwdResetInitiationSuccessful, setPwdResetInitiationSuccessful] = useState(false);
    const [pwdResetSuccessfulMsg, setPwdResetSuccessfulMsg] = useState<string>();
    const [otpRegenerationSuccessfulMsg, setOtpRegenerationSuccessfulMsg] = useState<string | undefined>('');
    const [otpRegenerationFailedMsg, setOtpRegenerationFailedMsg] = useState<string | undefined>('');
    const [responseErrorMessage, setResponseError] = useState<string | undefined>('');
    const [pwdResetSuccessful, setPwdResetSuccessful] = useState(false);
    const [initiatingPwdReset, setInitiatingPwdReset] = useState(false);
    const [regeneratingOtp, setRegeneratingOtp] = useState(false);
    const [completingPwdReset, setCompletingPwdReset] = useState(false);

    const handleChange = (e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setFormData({
            ...formData,
            [e.target.name]: newValue.value || ''
        });
    };

    const onInitiatePasswordReset = async () =>
    {
        if (!validator.isEmail(formData.email))
        {
            setPwdResetInitiationSuccessful(false);
            setResponseError("Please enter a valid email address");
            return
        }

        if (initiatingPwdReset) return;

        setPwdResetSuccessfulMsg("");
        setResponseError('');
        setInitiatingPwdReset(true);

        try
        {
            await initiatePasswordReset({email: formData.email});
            setPwdResetInitiationSuccessful(true);
            setPwdResetSuccessfulMsg("If you have an account with us, an OTP has been sent to your email address.");
        }
        catch (error)
        {
            setPwdResetInitiationSuccessful(false);
            setResponseError((error as ResponseError)?.errorMessage);
        }
        finally
        {
            setInitiatingPwdReset(false);
        }
    };

    const onCompletePasswordReset = async () =>
    {
        if (completingPwdReset) return;

        if (!formData.otp || !formData.otp)
        {
            setResponseError('Please enter the OTP sent to your email address');
            return;
        }

        if (!formData.password || !formData.password.length)
        {
            setResponseError('Please enter a new password');
            return;
        }

        if (!formData.confirmationPassword || !formData.confirmationPassword.length)
        {
            setResponseError('Please confirm your new password');
            return;
        }

        setPwdResetSuccessfulMsg('');
        setResponseError('');
        setCompletingPwdReset(true);

        try
        {
            await completePasswordReset(formData);
            setPwdResetSuccessful(true);
        }
        catch (error)
        {
            setResponseError((error as ResponseError)?.errorMessage);
        }
        finally
        {
            setCompletingPwdReset(false);
        }
    };

    const onRegenerateOTP = async () =>
    {
        if (regeneratingOtp) return;

        setFormData({...formData, otp: ''});
        setOtpRegenerationSuccessfulMsg('');
        setOtpRegenerationFailedMsg('');
        setRegeneratingOtp(true);

        try
        {
            const response = await regeneratePasswordResetOtp({email: formData.email});
            setOtpRegenerationSuccessfulMsg(response?.message);
        }
        catch (error)
        {
            setOtpRegenerationFailedMsg((error as ResponseError)?.errorMessage);
        }
        finally
        {
            setRegeneratingOtp(false);
        }
    };

    const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>, action: () => void) =>
    {
        if (event.key === 'Enter')
        {
            action();
        }
    };

    const renderFormErrorMessage = () => (
        responseErrorMessage && (
            <MessageBar intent={"error"}>
                <MessageBarBody>
                    <Text size={200}> {responseErrorMessage} </Text>
                </MessageBarBody>
                <MessageBarActions
                    containerAction={
                        <Button
                            onClick={() => setResponseError(undefined)}
                            appearance="transparent"
                            icon={<DismissRegular/>}
                        />
                    }
                />
            </MessageBar>
        )
    );

    const renderOtpSection = () => (
        <>
            <Field
                label={"OTP"}
                validationState={otpRegenerationFailedMsg ? "error" : (otpRegenerationSuccessfulMsg ? "success" : "none")}
                validationMessage={otpRegenerationFailedMsg || otpRegenerationSuccessfulMsg}>
                <Input type="text"
                       name="otp"
                       value={formData.otp}
                       autoComplete="false"
                       onChange={handleChange}
                       onKeyDown={(e) => handleKeyDown(e, onCompletePasswordReset)}/>
            </Field>
            <Button onClick={onRegenerateOTP}
                    size={"small"}
                    disabled={completingPwdReset}
                    appearance={"transparent"}
                    className={globalStyles.buttonWithLoading}>
                {regeneratingOtp && <Spinner size={"tiny"}/>}
                Resend OTP
            </Button>
        </>
    );

    const renderPasswordsSection = () => (
        <>
            <Field
                label={"Password"}
                validationState={"none"}
                validationMessage={""}>
                <Input type="password"
                       name="password"
                       value={formData.password}
                       disabled={regeneratingOtp}
                       onChange={handleChange}
                       onKeyDown={(e) => handleKeyDown(e, onCompletePasswordReset)}
                       contentAfter={
                           <InfoLabel info={<>
                               <strong>Password requirements</strong>
                               <ul>
                                   <li>Must be at least 8 characters long</li>
                                   <li>Must not exceed 30 characters</li>
                                   <li>Must contain at least one uppercase letter</li>
                                   <li>Must contain at least one lowercase letter</li>
                                   <li>Must contain at least one digit</li>
                                   <li>Must contain at least one special character</li>
                               </ul>
                           </>}/>
                       }/>
            </Field>
            <Field
                label={"Password Confirmation"}
                validationState={"none"}
                validationMessage={""}>
                <Input type={"password"}
                       name="confirmationPassword"
                       value={formData.confirmationPassword}
                       disabled={regeneratingOtp}
                       onChange={handleChange}
                       onKeyDown={(e) => handleKeyDown(e, onCompletePasswordReset)}/>
            </Field>
        </>
    );

    return (
        <section className={authorizationStyles.auth}>
            <section className={authorizationStyles.authSection}>
                <section className={authorizationStyles.authSection1}>
                    <div>
                        <AppLogo/>
                    </div>
                    {!pwdResetSuccessful &&
                        <>
                            <div className={authorizationStyles.authorizationFormSection}>
                                <Subtitle1 align={"center"}> Recover account </Subtitle1>

                                {renderFormErrorMessage()}

                                <Field
                                    label={"Email"}
                                    validationState={pwdResetSuccessfulMsg ? "success" : "none"}
                                    validationMessage={pwdResetSuccessfulMsg}>
                                    <Input type="email"
                                           name="email"
                                           disabled={initiatingPwdReset}
                                           autoComplete={"false"}
                                           value={formData.email}
                                           onChange={handleChange}
                                           onKeyDown={(e) => handleKeyDown(e, onInitiatePasswordReset)}/>
                                </Field>

                                {pwdResetInitiationSuccessful &&
                                    <div className={accountRecoveryStyles.signUpCompletionForm}>
                                        {renderOtpSection()}
                                        {renderPasswordsSection()}
                                        <Button onClick={onCompletePasswordReset}
                                                appearance={"primary"}
                                                shape={"circular"}
                                                disabled={regeneratingOtp}
                                                className={globalStyles.buttonWithLoading}>
                                            {completingPwdReset && <Spinner size={"tiny"}/>}
                                            {completingPwdReset ? "Resetting password" : "Reset Password"}
                                        </Button>
                                    </div>
                                }

                                {!pwdResetInitiationSuccessful &&
                                    <Button onClick={onInitiatePasswordReset}
                                            appearance={"primary"}
                                            shape={"circular"}
                                            className={globalStyles.buttonWithLoading}>
                                        {initiatingPwdReset && <Spinner size={"tiny"}/>}
                                        Initiate Password Reset
                                    </Button>
                                }
                                <div className={accountRecoveryStyles.authHasAccount}>
                                    <Caption1> Don't have an account? &nbsp;
                                        <Link onClick={() => navigate("/sign-up")}
                                              disabled={initiatingPwdReset || completingPwdReset}>
                                            <Text weight="semibold">Sign up</Text>
                                        </Link>
                                    </Caption1>
                                </div>
                            </div>
                        </>
                    }
                    {pwdResetSuccessful &&
                        <>
                            <section className={accountRecoveryStyles.passwordResetSuccessfulSection}>
                                <Text align={"center"} size={500} font="monospace">
                                    Password Reset Successful!
                                </Text>
                                <Text align={"center"} size={300}>
                                    Your password has been updated successfully.
                                </Text>
                                <Text align={"center"} italic>
                                    For added security, consider enabling 2FA to protect your account. Also, ensure your
                                    new password is
                                    strong and stored securely in a trusted password manager.
                                </Text>
                                <Button onClick={() => navigate("/sign-in")} appearance={"primary"} shape={"circular"}>
                                    Sign In
                                </Button>
                            </section>
                            <div>.</div>
                        </>
                    }
                </section>
                <section className={authorizationStyles.authSection2}>
                    <AccountRecoveryCarousel/>
                </section>
            </section>
        </section>
    );
};

export default AccountRecovery;