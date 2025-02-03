import React, {useState} from 'react';
import './SignIn.css';
import {completeSignIn, fetchAppUser, fetchAppUserPersonCompany, initiateSignIn} from '../../services/api';
import {ResponseError} from '../../services/models/models';
import {useAuth} from '../../context/AuthContext';
import {useNavigate} from 'react-router-dom';
import RedirectIfAuthenticated from '../components/RedirectIfAuthenticated';
import useToken from "../../context/useToken.tsx";
import {Button, Field, Input, Link} from "@fluentui/react-components";
import {AppUser} from "../models/models.tsx";
import {setApiClientAuthToken} from '../../services/apiClient';

const SignIn: React.FC = () =>
{
    const [email, setEmail] = useState('');
    const [otp, setOtp] = useState('');
    const [password, setPassword] = useState('');
    const [signInInitiationSuccessfulMsg, setSignInInitiationSuccessfulMsg] = useState<string>('');
    const [signInInitiationSuccessful, setSignInInitiationSuccessful] = useState<boolean>(false);
    const [responseErrorMessage, setResponseErrorMessage] = useState<string | undefined>('');
    const {setToken, setAppUser, setAppUserPersonCompany} = useAuth();
    const navigate = useNavigate();

    const onEmailChange = (_e: any, newValue?: any) => setEmail(newValue.value || '');
    const onOtpChange = (_e: any, newValue?: any) => setOtp(newValue.value || '');
    const onPasswordChange = (_e: any, newValue?: any) => setPassword(newValue.value || '');

    const token = useToken()

    const onInitiateSignIn = async () =>
    {
        try
        {
            const signInInitiateRequest = {email, password};
            const response = await initiateSignIn(signInInitiateRequest);
            setSignInInitiationSuccessfulMsg(response?.message);
            setSignInInitiationSuccessful(true);
        }
        catch (error)
        {
            setSignInInitiationSuccessful(false);
            setResponseErrorMessage((error as ResponseError)?.errorMessage);
        }
    };

    const onCompleteSignIn = async () =>
    {
        try
        {
            const signInCompletionRequest = {email, otp};
            const response = await completeSignIn(signInCompletionRequest);
            setToken(response.token);
            setApiClientAuthToken(response.token);

            let appUser: AppUser | null = null

            try
            {
                appUser = await fetchAppUser(token);
                setAppUser(appUser);
            }
            catch (error)
            {

            }

            try
            {
                if (appUser)
                {
                    const company = await fetchAppUserPersonCompany(appUser.id, appUser.person?.id, token?.toString());
                    setAppUserPersonCompany(company)
                }
            }
            catch (error)
            {

            }

            navigate('/landing');
        }
        catch (error)
        {
            setResponseErrorMessage((error as ResponseError)?.errorMessage);
        }

    };

    return (
        <RedirectIfAuthenticated element={
            <div>
                <h1>Sign In | <Link href={"/sign-up"}>Sign Up</Link></h1>

                {responseErrorMessage &&
                    <p>
                        {responseErrorMessage}
                    </p>
                }

                <Field
                    label={"Email"}
                    validationState={"none"}
                    validationMessage={""}>
                    <Input
                        value={email}
                        type="email"
                        onChange={onEmailChange}/>
                </Field>

                <Field
                    label={"Password"}
                    validationState={"none"}
                    validationMessage={""}>
                    <Input
                        type="password"
                        value={password}
                        onChange={onPasswordChange}/>
                </Field>

                {!signInInitiationSuccessful &&
                    <Button onClick={onInitiateSignIn}>Sign In </Button>
                }

                {signInInitiationSuccessful && (
                    <>
                        <p>{signInInitiationSuccessfulMsg}</p>

                        <Field
                            label={"OTP"}
                            validationState={"none"}
                            validationMessage={""}>
                            <Input
                                value={otp}
                                autoComplete="false"
                                onChange={onOtpChange}/>
                        </Field>
                        <Button onClick={onCompleteSignIn}> Complete Sign In</Button>
                    </>
                )}
            </div>
        }/>
    );
};

export default SignIn;