import React, {useState} from 'react';
import {TextField, PrimaryButton, Link} from '@fluentui/react';
import './SignUp.css';
import {completeSignUp, initiateSignUp, regenerateSignUpOtp} from "../../services/api.ts";
import {ResponseError} from "../../services/models/models.tsx";
import {useNavigate} from "react-router-dom";

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

    function onEmailChange(e, newValue: any)
    {
        return setEmail(newValue || '')
    }

    function onOtpChange(e, newValue)
    {
        setOtpRegenerationSuccessfulMsg('')
        setOtpRegenerationFailedMsg('')
        return setOtp(newValue || '')
    }

    function onPasswordChange(e, newValue)
    {
        return setPassword(newValue || '')
    }

    function onPasswordConfirmationChange(e, newValue)
    {
        return setConfirmationPassword(newValue || '')
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
                    <TextField label="Email"
                               value={email}
                               onChange={onEmailChange}/>

                    {initiationSuccessful && <>
                        <p className="success">
                            {initiationSuccessfulMsg}
                        </p>

                        <TextField label="OTP"
                                   value={otp}
                                   onChange={onOtpChange}/>

                        <p>{otpRegenerationSuccessfulMsg}</p>

                        <p>{otpRegenerationFailedMsg}</p>

                        <PrimaryButton text="Regenerate OTP"
                                       onClick={onRegenerateOTP}/>

                        <TextField label="Password"
                                   value={password}
                                   onChange={onPasswordChange}/>

                        <TextField label="Password Confirmation"
                                   value={confirmationPassword}
                                   onChange={onPasswordConfirmationChange}/>

                        <PrimaryButton text="Finish Sign up"
                                       onClick={onCompleteSignUp}/>

                        <p>{responseErrorMessage}</p>
                    </>
                    }

                    {!initiationSuccessful &&
                        <PrimaryButton text="Sign Up" onClick={onInitiateSignUp}/>
                    }
                </section>
            }

            {signUpSuccessful &&
                <section>
                    <p>Sign up successful</p>
                    <PrimaryButton text="Sign In" onClick={() => navigate("/sign-in")}/>
                </section>
            }
        </>
    );
};

export default SignUp;