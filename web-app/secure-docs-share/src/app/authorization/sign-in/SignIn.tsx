import React, {useState} from 'react';
import './SignIn.css';
import '../Authorization.css';
import {
    completeSignIn,
    fetchAppUser,
    fetchAppUserPersonCompany,
    initiateSignIn,
    regenerateSignInOtp
} from '../../../services/api.ts';
import {ResponseError} from '../../../services/models/models.tsx';
import {useAuth} from '../../../context/AuthContext.tsx';
import {useNavigate} from 'react-router-dom';
import RedirectIfAuthenticated from '../../components/RedirectIfAuthenticated.tsx';
import useToken from "../../../context/useToken.tsx";
import {
    Button, Caption1,
    Card,
    CardFooter,
    Divider,
    Field,
    Input,
    InputOnChangeData, Link,
    MessageBar,
    MessageBarActions,
    MessageBarBody,
    MessageBarTitle,
    Spinner,
    Subtitle1, Text,
} from "@fluentui/react-components";
import {AppUser} from "../../models/models.tsx";
import {setApiClientAuthToken} from '../../../services/apiClient.ts';
import {DismissRegular} from "@fluentui/react-icons";
import AppLogo from "../../components/app-logo/AppLogo.tsx";
import SignInCarousel from "../carousel/SignInCarousel.tsx";

const SignIn: React.FC = () =>
{
    const [email, setEmail] = useState<string>('');
    const [otp, setOtp] = useState<string>('');
    const [password, setPassword] = useState<string>('');
    const [signInInitiating, setSignInInitiating] = useState<boolean>(false);
    const [signInCompleting, setSignInCompleting] = useState<boolean>(false);
    const [resendingOtp, setResendingOtp] = useState<boolean>(false);
    const [signInInitiationSuccessfulMsg, setSignInInitiationSuccessfulMsg] = useState<string>('');
    const [signInInitiationSuccessful, setSignInInitiationSuccessful] = useState<boolean>(false);
    const [responseErrorMessage, setResponseErrorMessage] = useState<string | undefined>('');
    const {setToken, setAppUser, setAppUserPersonCompany} = useAuth();
    const navigate = useNavigate();

    const onEmailChange = (_e: React.ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) => setEmail(newValue.value || '');
    const onOtpChange = (_e: React.ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) => setOtp(newValue.value || '');
    const onPasswordChange = (_e: React.ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) => setPassword(newValue.value || '');

    const token = useToken();

    const onInitiateSignIn = async () =>
    {
        if (signInInitiating) return;

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
        if (signInCompleting) return;

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
                setResponseErrorMessage((error as ResponseError)?.errorMessage);
                setToken(null);
                setApiClientAuthToken(null);
                return;
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
            setSignInCompleting(false);
        }
    };

    const onResendOtp = async () =>
    {
        setResponseErrorMessage(undefined);
        setResendingOtp(true);

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
            setResendingOtp(false);
        }
    };

    const renderInitiateSignInButton = () => (
        <Button onClick={onInitiateSignIn}
                appearance="primary"
                className={"button-w-loading"}
                shape={"circular"}>
            {signInInitiating &&
                <>
                    <Spinner size={"extra-small"}/>
                    Initiating sign in
                </>
            }
            {!signInInitiating && "Sign In"}
        </Button>
    );

    const renderCompleteSignInButton = () => (
        <Button onClick={onCompleteSignIn}
                appearance="primary"
                className={"button-w-loading"}
                shape={"circular"}>
            {signInCompleting &&
                <>
                    <Spinner size={"extra-small"}/>
                    Completing sign in
                </>
            }
            {!signInCompleting && "Complete Sign In"}
        </Button>
    );

    const renderOtpSection = () => (
        <>
            <span>{signInInitiationSuccessfulMsg}</span>

            <Field label={"OTP"}
                   validationState={"none"}
                   validationMessage={""}
                   hint="Please check your email for the OTP.">
                <Input value={otp} autoComplete="false" onChange={onOtpChange}/>
            </Field>
            <span>
                <Button appearance="transparent"
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
    );

    const renderErrorMessage = () => (
        responseErrorMessage && (
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
        )
    );

    return (
        <RedirectIfAuthenticated element={

            <section id="auth">
                <section id="auth-section">
                    <section id="auth-section-1">
                        <div>
                            <AppLogo/>
                        </div>

                        <div id="authorization-form-section">

                            <Subtitle1 align={"center"}> Sign in</Subtitle1>

                            {renderErrorMessage()}

                            <Field label={"Email"} validationState={"none"} validationMessage={""}>
                                <Input value={email} type="email" onChange={onEmailChange}/>
                            </Field>

                            <Field label={"Password"} validationState={"none"} validationMessage={""}>
                                <Input type="password" value={password} onChange={onPasswordChange}/>
                            </Field>

                            {signInInitiationSuccessful && renderOtpSection()}

                            <CardFooter action={
                                <>
                                    {!signInInitiationSuccessful && renderInitiateSignInButton()}
                                    {signInInitiationSuccessful && renderCompleteSignInButton()}
                                </>
                            }>
                                <Button onClick={() => navigate('/forgot-password')}
                                        appearance="transparent">
                                    Forgot Password
                                </Button>
                            </CardFooter>


                            <div id="auth-no-account">
                                <Caption1> Don't have an account? &nbsp;
                                    <Link onClick={() => navigate("/sign-up")}
                                    disabled={signInInitiating || signInCompleting}>
                                        <Text weight="semibold">Sign up</Text>
                                    </Link>
                                </Caption1>
                            </div>
                        </div>
                        <span>.</span>
                    </section>
                    <section id="auth-section-2">
                        <SignInCarousel/>
                    </section>
                </section>
            </section>
        }/>
    );
};

export default SignIn;