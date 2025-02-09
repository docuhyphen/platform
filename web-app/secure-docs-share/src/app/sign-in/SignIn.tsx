import React, {useState} from 'react';
import './SignIn.css';
import {
    completeSignIn,
    fetchAppUser,
    fetchAppUserPersonCompany,
    initiateSignIn,
    regenerateSignInOtp
} from '../../services/api';
import {ResponseError} from '../../services/models/models';
import {useAuth} from '../../context/AuthContext';
import {useNavigate} from 'react-router-dom';
import RedirectIfAuthenticated from '../components/RedirectIfAuthenticated';
import useToken from "../../context/useToken.tsx";
import {
    Button,
    Card,
    CardFooter,
    Divider,
    Field,
    Input,
    MessageBar,
    MessageBarActions,
    MessageBarBody,
    MessageBarTitle,
    Spinner,
    Subtitle1,
} from "@fluentui/react-components";
import {AppUser} from "../models/models.tsx";
import {setApiClientAuthToken} from '../../services/apiClient';
import {DismissRegular} from "@fluentui/react-icons";

const SignIn: React.FC = () =>
{
    const [email, setEmail] = useState('');
    const [otp, setOtp] = useState('');
    const [password, setPassword] = useState('');
    const [signInInitiating, setSignInInitiating] = useState(false);
    const [signInCompleting, setSignInCompleting] = useState(false);
    const [resendingOtp, setResendingOtp] = useState(false);
    const [signInInitiationSuccessfulMsg, setSignInInitiationSuccessfulMsg] = useState<string>('');
    const [signInInitiationSuccessful, setSignInInitiationSuccessful] = useState<boolean>(false);
    const [responseErrorMessage, setResponseErrorMessage] = useState<string | undefined>('');
    const {setToken, setAppUser, setAppUserPersonCompany} = useAuth();
    const navigate = useNavigate();

    const onEmailChange = (_e: any, newValue?: any) => setEmail(newValue.value || '');
    const onOtpChange = (_e: any, newValue?: any) => setOtp(newValue.value || '');
    const onPasswordChange = (_e: any, newValue?: any) => setPassword(newValue.value || '');

    const token = useToken();

    const onInitiateSignIn = async () =>
    {
        if (signInInitiating)
        {
            return
        }

        if (!email || !password)
        {
            setResponseErrorMessage("Email and password are required.");
            return;
        }

        setSignInInitiating(true);
        setResponseErrorMessage(undefined);

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
        finally
        {
            setSignInInitiating(false);
        }
    };

    const onCompleteSignIn = async () =>
    {
        if (signInCompleting)
        {
            return
        }

        if (!otp)
        {
            setResponseErrorMessage("OTP is required.");
            return;
        }

        setResponseErrorMessage(undefined);
        setSignInCompleting(true);

        try
        {
            const signInCompletionRequest = {email, otp};
            const response = await completeSignIn(signInCompletionRequest);
            setToken(response.token);
            setApiClientAuthToken(response.token);

            let appUser: AppUser | null = null;

            try
            {
                appUser = await fetchAppUser(token);
                setAppUser(appUser);
            }
            catch (error)
            {
                // Handle error
            }

            try
            {
                if (appUser)
                {
                    const company = await fetchAppUserPersonCompany(appUser.id, appUser.person?.id, token?.toString());
                    setAppUserPersonCompany(company);
                }
            }
            catch (error)
            {
                // Handle error
            }

            navigate('/landing');
        }
        catch (error)
        {
            setResponseErrorMessage((error as ResponseError)?.errorMessage);
        }
        finally
        {
            setSignInCompleting(false)
        }
    };

    const onResendOtp = async () =>
    {
        setResponseErrorMessage(undefined);
        setResendingOtp(true)

        try
        {
            await regenerateSignInOtp({email});
        }
        catch (error)
        {
            setResponseErrorMessage((error as ResponseError)?.errorMessage);
        }
        finally
        {
            setResendingOtp(false)
        }
    }

    return (
        <RedirectIfAuthenticated element={
            <section id="sign-in-section">
                <Card id="sign-in-card">

                    <Divider appearance={"brand"}> <Subtitle1> Sign in </Subtitle1></Divider>

                    {responseErrorMessage &&
                        <MessageBar intent={"error"}>
                            <MessageBarBody>
                                <MessageBarTitle>Error: </MessageBarTitle>
                                {responseErrorMessage}
                            </MessageBarBody>
                            <MessageBarActions
                                containerAction={
                                    <Button
                                        onClick={() => setResponseErrorMessage(undefined)}
                                        appearance="transparent"
                                        icon={<DismissRegular/>}
                                    />
                                }
                            />
                        </MessageBar>
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

                    {signInInitiationSuccessful && (
                        <>
                            <span>{signInInitiationSuccessfulMsg}</span>

                            <Field
                                label={"OTP"}
                                validationState={"none"}
                                validationMessage={""}>
                                <Input
                                    value={otp}
                                    autoComplete="false"
                                    onChange={onOtpChange}/>
                            </Field>
                            <span>
                                <Button appearance="outline"
                                        size={"small"}
                                        onClick={onResendOtp}
                                        className={"button-w-loading"}>
                                    <>
                                        {resendingOtp && <Spinner size={"tiny"}/>}
                                        Resend OTP
                                    </>
                                </Button>
                            </span>
                        </>
                    )}

                    <CardFooter action={
                        <>
                            {!signInInitiationSuccessful &&
                                <Button onClick={onInitiateSignIn}
                                        appearance="primary"
                                        className={"button-w-loading"}>
                                    {signInInitiating &&
                                        <>
                                            <Spinner size={"extra-small"}/>
                                            Initiating sign in
                                        </>
                                    }
                                    {!signInInitiating && "Sign In"}
                                </Button>
                            }

                            {signInInitiationSuccessful && (
                                <>
                                    <Button onClick={onCompleteSignIn}
                                            appearance="primary"
                                            className={"button-w-loading"}>
                                        {signInCompleting &&
                                            <>
                                                <Spinner size={"extra-small"}/>
                                                Completing sign in
                                            </>
                                        }
                                        {!signInCompleting && "Complete Sign In"}
                                    </Button>
                                </>
                            )}
                        </>
                    }>
                        <Button onClick={() => navigate('/forgot-password')}
                                appearance="subtle">
                            Forgot Password
                        </Button>
                    </CardFooter>

                    <Divider appearance={"brand"}> OR </Divider>

                    <Button onClick={() => navigate("/sign-up")}
                            appearance={"subtle"}>
                        Sign Up
                    </Button>
                </Card>
            </section>
        }/>
    );
};

export default SignIn;