import React, {useState} from 'react';
import {TextField, PrimaryButton} from '@fluentui/react';
import './SignIn.css';
import {initiateSignIn, completeSignIn} from "../../services/api.ts";
import {ResponseError} from "../../services/models/models.tsx";

const SignIn: React.FC = () =>
{
    const [email, setEmail] = useState('');
    const [otp, setOtp] = useState('');
    const [password, setPassword] = useState('');
    const [signInInitiationSuccessfulMsg, setSignInInitiationSuccessfulMsg] = useState<boolean>(false);
    const [signInInitiationSuccessful, setSignInInitiationSuccessful] = useState<boolean>(false);
    const [responseErrorMessage, setResponseErrorMessage] = useState<string | undefined>('');

    function onEmailChange(e, newValue)
    {
        setEmail(newValue || '')
    }

    function onOtpChange(e, newValue)
    {
        setOtp(newValue || '')
    }

    function onPasswordChange(e, newValue)
    {
        setPassword(newValue || '')
    }

    const onInitiateSignIn = async () =>
    {
        try
        {
            const signInInitiateRequest = {email, password};
            const response = await initiateSignIn(signInInitiateRequest);

            setSignInInitiationSuccessfulMsg(response?.message);
            setSignInInitiationSuccessful(true)
        }
        catch (error)
        {
            setSignInInitiationSuccessful(false)
            setResponseErrorMessage((error as ResponseError)?.errorMessage);
        }
    };

    const onCompleteSignIn = async () =>
    {
        try
        {
            const signInCompletionRequest = {
                email,
                otp
            };
            await completeSignIn(signInCompletionRequest);
        }
        catch (error)
        {
            setResponseErrorMessage((error as ResponseError)?.errorMessage);
        }
    };

    return (
        <div>
            <h1>Sign In</h1>
            {responseErrorMessage &&
                <p className="error">
                    {responseErrorMessage}
                </p>
            }

            <TextField label="Email"
                       value={email}
                       type="email"
                       onChange={onEmailChange}/>

            <TextField label="Password"
                       type="password"
                       value={password}
                       onChange={onPasswordChange}/>

            {!signInInitiationSuccessful &&
                <PrimaryButton text="Sign In" onClick={onInitiateSignIn}/>
            }

            {signInInitiationSuccessful &&
                <>
                    <p>
                        {signInInitiationSuccessfulMsg}
                    </p>

                    <TextField label="OTP"
                               value={otp}
                               onChange={onOtpChange}/>

                    <PrimaryButton text="Complete Sign In" onClick={onCompleteSignIn}/>
                </>
            }

        </div>
    );
};

export default SignIn;