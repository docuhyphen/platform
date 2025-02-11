import React, {ChangeEvent, useState} from 'react';
import './SignUp.css';
import {completeSignUp, initiateSignUp, regenerateSignUpOtp} from "../../services/api.ts";
import {ResponseError} from "../../services/models/models.tsx";
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
import AppLogo from "../components/app-logo/AppLogo.tsx";
import SignUpCarousel from "../components/carousel/SignUpCarousel.tsx";

const SignUp: React.FC = () =>
{
    const navigate = useNavigate();

    const [formData, setFormData] = useState({
        email: '',
        otp: '',
        password: '',
        confirmationPassword: ''
    });

    const [initiationSuccessful, setInitiationSuccessful] = useState(false);
    const [initiationSuccessfulMsg, setInitiationSuccessfulMsg] = useState<string>();
    const [otpRegenerationSuccessfulMsg, setOtpRegenerationSuccessfulMsg] = useState<string | undefined>('');
    const [otpRegenerationFailedMsg, setOtpRegenerationFailedMsg] = useState<string | undefined>('');
    const [responseErrorMessage, setResponseError] = useState<string | undefined>('');
    const [signUpSuccessful, setSignUpSuccessful] = useState(false);
    const [initiatingSignUp, setInitiatingSignUp] = useState(false);
    const [regeneratingOtp, setRegeneratingOtp] = useState(false);
    const [completingSignUp, setCompletingSignUp] = useState(false);

    const handleChange = (e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setFormData({
            ...formData,
            [e.target.name]: newValue.value || ''
        });
    };

    const onInitiateSignUp = async () =>
    {
        if (initiatingSignUp) return;

        setInitiationSuccessfulMsg("");
        setResponseError('');
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
            setResponseError((error as ResponseError)?.errorMessage);
        }
        finally
        {
            setInitiatingSignUp(false);
        }
    };

    const onCompleteSignUp = async () =>
    {
        if (completingSignUp) return;

        setInitiationSuccessfulMsg('');
        setResponseError('');
        setCompletingSignUp(true);

        try
        {
            await completeSignUp(formData);
            setSignUpSuccessful(true);
        }
        catch (error)
        {
            setResponseError((error as ResponseError)?.errorMessage);
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

    const renderFormErrorMessage = () => (
        responseErrorMessage && (
            <MessageBar intent={"error"}>
                <MessageBarBody>
                    {responseErrorMessage}
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
                       onChange={handleChange}/>
            </Field>
            <Button onClick={onRegenerateOTP}
                    size={"small"}
                    disabled={completingSignUp}
                    appearance={"transparent"}
                    className={"button-w-loading"}>
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
                       onChange={handleChange}/>
            </Field>
        </>
    );

    return (
        <section id="auth">
            <section id="auth-section">
                <section id="auth-section-1">
                    <div>
                        <AppLogo/>
                    </div>
                    {!signUpSuccessful && <>
                        <div id="authorization-form-section">
                            <Subtitle1 align={"center"}> Create account </Subtitle1>
                            {renderFormErrorMessage()}
                            <Field
                                label={"Email"}
                                validationState={initiationSuccessfulMsg ? "success" : "none"}
                                validationMessage={initiationSuccessfulMsg}>
                                <Input type="email"
                                       name="email"
                                       autoComplete={"false"}
                                       value={formData.email}
                                       onChange={handleChange}/>
                            </Field>
                            {initiationSuccessful && <div id={"sign-up-completion-form"}>
                                {renderOtpSection()}
                                {renderPasswordsSection()}
                                <Button onClick={onCompleteSignUp}
                                        appearance={"primary"}
                                        shape={"circular"}
                                        disabled={regeneratingOtp}
                                        className={"button-w-loading"}>
                                    {completingSignUp && <Spinner size={"extra-small"}/>}
                                    {completingSignUp ? "Completing sign up" : "Complete sign up"}
                                </Button>
                            </div>}
                            {!initiationSuccessful &&
                                <Button onClick={onInitiateSignUp}
                                        appearance={"primary"}
                                        shape={"circular"}
                                        className={"button-w-loading"}>
                                    {initiatingSignUp && <Spinner size={"extra-small"}/>}
                                    Sign Up
                                </Button>}
                            <div id="auth-has-account">
                                <Caption1> Already have an account? &nbsp;
                                    <Link onClick={() => navigate("/sign-in")}>
                                        <Text weight="semibold">Sign In</Text>
                                    </Link>
                                </Caption1>
                            </div>
                        </div>
                        <span>.</span>
                    </>}
                    {signUpSuccessful && <>
                        <section id={"sign-up-successful-section"}>
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
                                Your Security is our priority, remember to setup your 2FA to help us keep your account
                                secure, also remember to keep your password safe and secure with a trusted password manager.
                            </Text>
                            <Button onClick={() => navigate("/sign-in")}
                                    appearance={"primary"}>
                                Sign In
                            </Button>
                        </section>
                        <div>.</div>
                    </>}
                </section>
                <section id="auth-section-2">
                    <SignUpCarousel/>
                </section>
            </section>
        </section>
    );
};

export default SignUp;