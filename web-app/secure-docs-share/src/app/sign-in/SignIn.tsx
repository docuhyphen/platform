import React, { useState } from 'react';
import { TextField, PrimaryButton } from '@fluentui/react';
import './SignIn.css';
import { initiateSignIn, completeSignIn } from '../../services/api';
import { ResponseError } from '../../services/models/models';
import { useAuth } from '../../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import RedirectIfAuthenticated from '../components/RedirectIfAuthenticated';

const SignIn: React.FC = () => {
    const [email, setEmail] = useState('');
    const [otp, setOtp] = useState('');
    const [password, setPassword] = useState('');
    const [signInInitiationSuccessfulMsg, setSignInInitiationSuccessfulMsg] = useState<string>('');
    const [signInInitiationSuccessful, setSignInInitiationSuccessful] = useState<boolean>(false);
    const [responseErrorMessage, setResponseErrorMessage] = useState<string | undefined>('');
    const { setToken } = useAuth();
    const navigate = useNavigate();

    const onEmailChange = (e: React.FormEvent<HTMLInputElement | HTMLTextAreaElement>, newValue?: string) => setEmail(newValue || '');
    const onOtpChange = (e: React.FormEvent<HTMLInputElement | HTMLTextAreaElement>, newValue?: string) => setOtp(newValue || '');
    const onPasswordChange = (e: React.FormEvent<HTMLInputElement | HTMLTextAreaElement>, newValue?: string) => setPassword(newValue || '');

    const onInitiateSignIn = async () => {
        try {
            const signInInitiateRequest = { email, password };
            const response = await initiateSignIn(signInInitiateRequest);
            setSignInInitiationSuccessfulMsg(response?.message);
            setSignInInitiationSuccessful(true);
        } catch (error) {
            setSignInInitiationSuccessful(false);
            setResponseErrorMessage((error as ResponseError)?.errorMessage);
        }
    };

    const onCompleteSignIn = async () => {
        try {
            const signInCompletionRequest = { email, otp };
            const response = await completeSignIn(signInCompletionRequest);
            setToken(response.token);
            navigate('/landing');
        } catch (error) {
            setResponseErrorMessage((error as ResponseError)?.errorMessage);
        }
    };

    return (
        <RedirectIfAuthenticated element={
            <div>
                <h1>Sign In</h1>
                {responseErrorMessage && <p className="error">{responseErrorMessage}</p>}
                <TextField label="Email" value={email} type="email" onChange={onEmailChange} />
                <TextField label="Password" type="password" value={password} onChange={onPasswordChange} />
                {!signInInitiationSuccessful && <PrimaryButton text="Sign In" onClick={onInitiateSignIn} />}
                {signInInitiationSuccessful && (
                    <>
                        <p>{signInInitiationSuccessfulMsg}</p>
                        <TextField label="OTP" value={otp} onChange={onOtpChange} />
                        <PrimaryButton text="Complete Sign In" onClick={onCompleteSignIn} />
                    </>
                )}
            </div>
        } />
    );
};

export default SignIn;