import React, {useState} from 'react';
import './SignUp.css';
import {completeSignUp, initiateSignUp, regenerateSignUpOtp} from "../../services/api.ts";
import {ResponseError} from "../../services/models/models.tsx";
import {useNavigate} from "react-router-dom";
import {Button, Input, Label, Link} from "@fluentui/react-components";

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

    function onEmailChange(newValue: any)
    {
        return setEmail(newValue.value || '')
    }

    function onOtpChange(newValue: any)
    {
        setOtpRegenerationSuccessfulMsg('')
        setOtpRegenerationFailedMsg('')
        return setOtp(newValue.value || '')
    }

    function onPasswordChange(newValue: any)
    {
        return setPassword(newValue.value || '')
    }

    function onPasswordConfirmationChange(_e: any, newValue: any)
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
                    <Label htmlFor="email">Email</Label>
                    <Input type="email"
                           id={"email"}
                               value={email}
                               onChange={onEmailChange}/>

                    {initiationSuccessful && <>
                        <p className="success">
                            {initiationSuccessfulMsg}
                        </p>

                        <Label htmlFor="otp">OTP</Label>
                        <Input type="text"
                               id={"otp"}
                                   value={otp}
                               autoComplete="false"
                                   onChange={onOtpChange}/>

                        <p>{otpRegenerationSuccessfulMsg}</p>

                        <p>{otpRegenerationFailedMsg}</p>

                        <Button onClick={onRegenerateOTP}> Regenerate OTP</Button>

                        <Label htmlFor="password">Password</Label>
                        <Input type="password"
                               id={"password"}
                                   value={password}
                                   onChange={onPasswordChange}/>

                        <Label htmlFor="passwordConfirmation">Password Confirmation</Label>
                        <Input type={"password"}
                               id={"passwordConfirmation"}
                                   value={confirmationPassword}
                                   onChange={onPasswordConfirmationChange}/>

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
                    <Button onClick={() => navigate("/sign-in")}> </Button>
                </section>
            }
        </>
    );
};

export default SignUp;