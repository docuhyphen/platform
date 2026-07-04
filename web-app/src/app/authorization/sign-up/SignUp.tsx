import React, {ChangeEvent, useState} from 'react';
import {completeSignUp, initiateSignUp, regenerateSignUpOtp} from "../../../services/authApi.ts";
import {useNavigate, useSearchParams} from "react-router-dom";
import {
    Button,
    Caption1,
    Divider,
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
import {ArrowLeftRegular, DismissRegular} from "@fluentui/react-icons";
import AppLogo from "../../components/app-logo/AppLogo.tsx";
import SignUpCarousel from "../carousel/SignUpCarousel.tsx";
import {useSignUpStyles} from "./SignUpStyles.tsx";
import {useAuthorizationStyles} from "../AuthorizationStyles.tsx";
import {useGlobalStyles} from "../../../GlobalStyles.tsx";
import {ResponseError} from "../../models/models.tsx";
import validator from 'validator';

interface SignUpFormData
{
    email: string;
    otp: string;
    password: string;
    confirmationPassword: string;
}

const SignUp: React.FC = () =>
{
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const signUpStyles = useSignUpStyles();
    const authorizationStyles = useAuthorizationStyles();
    const globalStyles = useGlobalStyles();

    const prefillEmail = searchParams.get('email') || '';

    const [formData, setFormData] = useState<SignUpFormData>({
        email: prefillEmail,
        otp: '',
        password: '',
        confirmationPassword: ''
    });

    const [initiationSuccessful, setInitiationSuccessful] = useState(false);
    const [initiationSuccessfulMsg, setInitiationSuccessfulMsg] = useState<string>();
    const [otpRegenerationSuccessfulMsg, setOtpRegenerationSuccessfulMsg] = useState<string | undefined>('');
    const [otpRegenerationFailedMsg, setOtpRegenerationFailedMsg] = useState<string | undefined>('');
    const [responseErrorMessage, setFormErrorMessage] = useState<string | undefined>('');
    const [signUpSuccessful, setSignUpSuccessful] = useState(false);
    const [initiatingSignUp, setInitiatingSignUp] = useState(false);
    const [regeneratingOtp, setRegeneratingOtp] = useState(false);
    const [completingSignUp, setCompletingSignUp] = useState(false);

    const handleChange = (e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setFormErrorMessage('');

        setFormData({
            ...formData,
            [e.target.name]: (newValue.value || '')
        });
    };

    const onInitiateSignUp = async () =>
    {
        if (!validator.isEmail(formData.email))
        {
            setFormErrorMessage("A valid email is required");
            return;
        }

        if (initiatingSignUp) return;

        setInitiationSuccessfulMsg("");
        setFormErrorMessage('');
        setInitiatingSignUp(true);

        try
        {
            const response = await initiateSignUp({email: formData.email});
            setInitiationSuccessful(true);
            setInitiationSuccessfulMsg(response?.message);
        }
        catch (error)
        {
            setInitiationSuccessful(false);
            setFormErrorMessage((error as ResponseError)?.errorMessage);
        }
        finally
        {
            setInitiatingSignUp(false);
        }
    };

    const isSignUpCompletionFormValid = () =>
    {
        if (!formData.otp)
        {
            setFormErrorMessage("Verification code is required");
            return false;
        }

        if (!formData.password)
        {
            setFormErrorMessage("Password is required");
            return false;
        }

        if (!formData.confirmationPassword)
        {
            setFormErrorMessage("Password confirmation is required");
            return false;
        }

        if (formData.password != formData.confirmationPassword)
        {
            setFormErrorMessage("Passwords do not match");
            return false;
        }

        return true;
    }

    const onCompleteSignUp = async () =>
    {
        if (!isSignUpCompletionFormValid())
        {
            return;
        }

        if (completingSignUp)
        {
            return;
        }

        setFormErrorMessage('');
        setCompletingSignUp(true);

        try
        {
            await completeSignUp(formData);
            setSignUpSuccessful(true);
        }
        catch (error)
        {
            setFormErrorMessage((error as ResponseError)?.errorMessage);
        }
        finally
        {
            setCompletingSignUp(false);
        }
    };

    const onRegenerateOTP = async () =>
    {
        if (regeneratingOtp) return;

        setFormData({...formData, otp: ''});
        setOtpRegenerationSuccessfulMsg('');
        setOtpRegenerationFailedMsg('');
        setRegeneratingOtp(true);
        setFormErrorMessage("")

        try
        {
            const response = await regenerateSignUpOtp({email: formData.email});
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
                            id={"sign-up-error-dismiss-btn"}
                            shape={"circular"}
                            onClick={() => setFormErrorMessage(undefined)}
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
                label={"Verification code"}
                validationState={otpRegenerationFailedMsg ? "error" : (otpRegenerationSuccessfulMsg ? "success" : "none")}
                validationMessage={otpRegenerationFailedMsg || otpRegenerationSuccessfulMsg}>
                <Input
                       id={"sign-up-otp-input"}
                       type="text"
                       name="otp"
                       maxLength={6}
                       value={formData.otp}
                       autoComplete="false"
                       onChange={handleChange}
                       onKeyDown={(e) => handleKeyDown(e, onCompleteSignUp)}/>
            </Field>
            <Button
                id={"sign-up-resend-otp-btn"}
                onClick={onRegenerateOTP}
                size={"small"}
                disabled={completingSignUp}
                appearance={"transparent"}
                shape={"circular"}
                className={globalStyles.buttonWithLoading}>
                {regeneratingOtp && <Spinner size={"tiny"}/>}
                Resend verification code
            </Button>
        </>
    );

    const renderPasswordsSection = () => (
        <>
            <Field
                label={"Password"}
                validationState={"none"}
                validationMessage={""}>
                <Input
                       id={"sign-up-password-input"}
                       type="password"
                       name="password"
                       maxLength={30}
                       value={formData.password}
                       disabled={regeneratingOtp}
                       onChange={handleChange}
                       onKeyDown={(e) => handleKeyDown(e, onCompleteSignUp)}
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
                <Input
                       id={"sign-up-confirm-password-input"}
                       type={"password"}
                       name="confirmationPassword"
                       maxLength={30}
                       value={formData.confirmationPassword}
                       disabled={regeneratingOtp}
                       onChange={handleChange}
                       onKeyDown={(e) => handleKeyDown(e, onCompleteSignUp)}/>
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
                    {!signUpSuccessful && <div className={authorizationStyles.authorizationFormSection}>
                        <Subtitle1 align={"center"}>
                            <Button
                                id={"sign-up-back-btn"}
                                shape={"circular"}
                                icon={<ArrowLeftRegular/>}
                                appearance={"transparent"}
                                onClick={() => navigate("/sign-in")}/>
                            Create account
                        </Subtitle1>
                        {renderFormErrorMessage()}
                        <Field
                            label={"Enter your email"}
                            validationState={initiationSuccessfulMsg ? "success" : "none"}
                            validationMessage={initiationSuccessfulMsg}>
                            <Input
                                   id={"sign-up-email-input"}
                                   type="email"
                                   name="email"
                                   maxLength={254}
                                   autoComplete={"false"}
                                   value={formData.email}
                                   onChange={handleChange}
                                   onKeyDown={(e) => handleKeyDown(e, onInitiateSignUp)}/>
                        </Field>
                        {initiationSuccessful && <div className={signUpStyles.signUpCompletionForm}>
                            {renderOtpSection()}
                            {renderPasswordsSection()}
                            <Button
                                id={"sign-up-complete-btn"}
                                onClick={onCompleteSignUp}
                                appearance={"primary"}
                                shape={"circular"}
                                disabled={regeneratingOtp}
                                className={globalStyles.buttonWithLoading}>
                                {completingSignUp && <Spinner size={"tiny"}/>}
                                {completingSignUp ? "Completing sign up" : "Complete sign up"}
                            </Button>
                        </div>}
                        {!initiationSuccessful &&
                            <Button
                                id={"sign-up-initiate-btn"}
                                onClick={onInitiateSignUp}
                                appearance={"primary"}
                                shape={"circular"}
                                className={globalStyles.buttonWithLoading}>
                                {initiatingSignUp && <Spinner size={"tiny"}/>}
                                Sign Up
                            </Button>}

                        {!initiationSuccessful && <>
                            <div><Divider>OR</Divider></div>

                            <Button
                                id={"sign-up-microsoft-btn"}
                                onClick={() => {
                                    const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;
                                    window.location.href = `${apiBaseUrl}/auth/oauth/MICROSOFT/authorize?flow=signup`;
                                }}
                                appearance={"secondary"}
                                shape={"circular"}>
                                Sign up with Microsoft
                            </Button>

                            <Button
                                id={"sign-up-google-btn"}
                                onClick={() => {
                                    const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;
                                    window.location.href = `${apiBaseUrl}/auth/oauth/GOOGLE/authorize?flow=signup`;
                                }}
                                appearance={"secondary"}
                                shape={"circular"}>
                                Sign up with Google
                            </Button>
                        </>}
                        <div className={signUpStyles.authHasAccount}>
                            <Caption1> Already have an account? &nbsp;
                                <Link onClick={() => navigate("/sign-in")}
                                      disabled={initiatingSignUp || completingSignUp}>
                                    <Text weight="semibold">Sign in</Text>
                                </Link>
                            </Caption1>
                        </div>
                    </div>}
                    {signUpSuccessful && <div className={signUpStyles.signUpSuccessfulSection}>
                        <Text align={"center"}
                              size={500}
                              font="monospace">
                            Sign up successful!
                        </Text>
                        <Text align={"center"}
                              size={300}>
                            Welcome aboard, your account has been created successfully.
                        </Text>
                        <Text align={"center"}
                              italic>
                            <strong> Your account is protected </strong>.
                            Two-factor authentication (2FA) is enabled by default to enhance
                            your account security. We also recommend keeping your password
                            secure with a trusted password manager.
                        </Text>
                        <Button
                            id={"sign-up-success-sign-in-btn"}
                            onClick={() => navigate("/sign-in")}
                            appearance={"primary"}
                            shape={"circular"}>
                            Sign In
                        </Button>
                    </div>}
                </section>
                <section className={authorizationStyles.authSection2}>
                    <SignUpCarousel/>
                </section>
            </section>
        </section>
    );
};

export default SignUp;