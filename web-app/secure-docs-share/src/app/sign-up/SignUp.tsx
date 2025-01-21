import React, {ChangeEvent, useState} from 'react';
import './SignUp.css';
import {completeSignUp, initiateSignUp, regenerateSignUpOtp} from "../../services/api.ts";
import {ResponseError} from "../../services/models/models.tsx";
import {useNavigate} from "react-router-dom";
import {Button, Field, Input, InputOnChangeData, Link} from "@fluentui/react-components";

const SignUp: React.FC = () =>
{
    const navigate = useNavigate();

    const [email, setEmail] = useState<string>('')
    const [otp, setOtp] = useState<string>('')
    const [password, setPassword] = useState<string>('')
    const [confirmationPassword, setConfirmationPassword] = useState<string>('')
    const [initiationSuccessful, setInitiationSuccessful] = useState<boolean>(false)
    const [initiationSuccessfulMsg, setInitiationSuccessfulMsg] = useState<string>('')
    const [otpRegenerationSuccessfulMsg, setOtpRegenerationSuccessfulMsg] = useState<string | undefined>('')
    const [otpRegenerationFailedMsg, setOtpRegenerationFailedMsg] = useState<string | undefined>('')
    const [responseErrorMessage, setResponseError] = useState<string | undefined>('')
    const [signUpSuccessful, setSignUpSuccessful] = useState<boolean>(false)

    function onEmailChange(_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData)
    {
        return setEmail(newValue.value || '')
    }

    function onOtpChange(_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData)
    {
        setOtpRegenerationSuccessfulMsg('')
        setOtpRegenerationFailedMsg('')
        return setOtp(newValue.value || '')
    }

    function onPasswordChange(_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData)
    {
        return setPassword(newValue.value || '')
    }

    function onPasswordConfirmationChange(_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData)
    {
        return setConfirmationPassword(newValue.value || '')
    }

    const onInitiateSignUp = async () =>
    {
        try
        {
            const signUpInitiateRequest = {email}
            const response = await initiateSignUp(signUpInitiateRequest);

            setInitiationSuccessful(true)
            setInitiationSuccessfulMsg(response?.message)
        }
        catch (error)
        {
            setInitiationSuccessful(false)
            setResponseError((error as ResponseError)?.errorMessage);
        }
    };

    const onCompleteSignUp = async () =>
    {
        setInitiationSuccessfulMsg('')
        setResponseError('')

        try
        {
            const signUpCompletionRequest = {
                email,
                otp,
                password,
                confirmationPassword: confirmationPassword
            }

            await completeSignUp(signUpCompletionRequest)

            setSignUpSuccessful(true)
        }
        catch (error)
        {
            setResponseError((error as ResponseError)?.errorMessage);
        }
    };

    const onRegenerateOTP = async () =>
    {
        setOtp('')
        setOtpRegenerationSuccessfulMsg('')
        setOtpRegenerationFailedMsg('')

        try
        {
            const otpRegenerationRequest = {email}
            const response = await regenerateSignUpOtp(otpRegenerationRequest)

            setOtpRegenerationSuccessfulMsg(response?.message)
        }
        catch (error)
        {
            setOtpRegenerationFailedMsg((error as ResponseError)?.errorMessage)
        }
    };

    return (
        <>
            {!signUpSuccessful &&
                <section>
                    <h1>Sign up | <Link onClick={() => navigate("/sign-in")}>Sign In</Link></h1>

                    {!initiationSuccessful && <>
                        <p>
                            {responseErrorMessage}
                        </p>
                    </>
                    }

                    <Field
                        label={"Email"}
                        validationState={"none"}
                        validationMessage={""}>
                        <Input type="email"
                               value={email}
                               onChange={onEmailChange}/>
                    </Field>

                    {initiationSuccessful && <>
                        <p className="success">
                            {initiationSuccessfulMsg}
                        </p>

                        <Field
                            label={"OTP"}
                            validationState={"none"}
                            validationMessage={""}>
                            <Input type="text"
                                   value={otp}
                                   autoComplete="false"
                                   onChange={onOtpChange}/>
                        </Field>

                        <p>{otpRegenerationSuccessfulMsg}</p>

                        <p>{otpRegenerationFailedMsg}</p>

                        <Button onClick={onRegenerateOTP}> Regenerate OTP</Button>

                        <Field
                            label={"Password"}
                            validationState={"none"}
                            validationMessage={""}>
                            <Input type="password"
                                   value={password}
                                   onChange={onPasswordChange}/>
                        </Field>

                        <Field
                            label={"Password Confirmation"}
                            validationState={"none"}
                            validationMessage={""}>
                            <Input type={"password"}
                                   value={confirmationPassword}
                                   onChange={onPasswordConfirmationChange}/>
                        </Field>
                        <Button onClick={onCompleteSignUp}> Finish Sign up</Button>

                        <p>{responseErrorMessage}</p>
                    </>
                    }

                    {!initiationSuccessful &&
                        <Button onClick={onInitiateSignUp}> Sign Up </Button>
                    }
                </section>
            }

            {signUpSuccessful &&
                <section>
                    <p>Sign up successful</p>
                    <Button onClick={() => navigate("/sign-in")}> Sign In</Button>
                </section>
            }
        </>
    );
};

export default SignUp;