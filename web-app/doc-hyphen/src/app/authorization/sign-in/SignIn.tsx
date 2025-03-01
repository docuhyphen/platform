import React, {useState} from 'react';
import {completeSignIn, initiateSignIn, regenerateSignInOtp} from '../../../services/authApi.ts';
import {fetchAppUser, fetchAppUserPersonCompany,} from '../../../services/userApi.ts';
import {useAuth} from '../../../context/AuthContext.tsx';
import {useNavigate} from 'react-router-dom';
import RedirectIfAuthenticated from '../../components/RedirectIfAuthenticated.tsx';
import useToken from "../../../context/useToken.tsx";
import {
    Button,
    Caption1,
    Divider,
    Field,
    Input,
    InputOnChangeData,
    Link,
    MessageBar,
    MessageBarActions,
    MessageBarBody,
    Spinner,
    Subtitle1,
    Text,
} from "@fluentui/react-components";
import {AppUser, ResponseError} from "../../models/models.tsx";
import {setApiClientAuthToken} from '../../../services/apiClient.ts';
import {DismissRegular} from "@fluentui/react-icons";
import AppLogo from "../../components/app-logo/AppLogo.tsx";
import SignInCarousel from "../carousel/SignInCarousel.tsx";
import {useSignInStyles} from "./SignInStyles.tsx";
import {useAuthorizationStyles} from "../AuthorizationStyles.tsx";
import {useGlobalStyles} from "../../../GlobalStyles.tsx";

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
    const signInStyles = useSignInStyles();
    const authorizationStyles = useAuthorizationStyles();
    const globalStyles = useGlobalStyles();

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

            navigate('/sharing-sessions');
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

    const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>, action: () => void) =>
    {
        if (event.key === 'Enter')
        {
            action();
        }
    };

    const renderInitiateSignInButton = () => (
        <Button onClick={onInitiateSignIn}
                appearance="primary"
                className={globalStyles.buttonWithLoading}
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
                className={globalStyles.buttonWithLoading}
                shape={"circular"}>
            {signInCompleting &&
                <>
                    <Spinner size={"extra-small"}/>
                    Completing sign in
                </>
            }
            {!signInCompleting && "Complete sign in"}
        </Button>
    );

    const renderOtpSection = () => (
        <>
            <span>{signInInitiationSuccessfulMsg}</span>

            <Field label={"OTP"}
                   validationState={"none"}
                   validationMessage={""}
                   hint="Please check your email for the OTP.">
                <Input value={otp}
                       autoComplete="false"
                       onChange={onOtpChange}
                       onKeyDown={(e) => handleKeyDown(e, onCompleteSignIn)}/>
            </Field>
            <span>
                <Button appearance="outline"
                        size={"small"}
                        shape={"circular"}
                        onClick={onResendOtp}
                        className={globalStyles.buttonWithLoading}>
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

            <section className={authorizationStyles.auth}>
                <section className={authorizationStyles.authSection}>
                    <section className={authorizationStyles.authSection1}>
                        <div>
                            <AppLogo/>
                        </div>

                        <div className={authorizationStyles.authorizationFormSection}>

                            <Subtitle1 align={"center"}> Sign in</Subtitle1>

                            {renderErrorMessage()}

                            <Field label={"Email"}
                                   validationState={"none"}
                                   validationMessage={""}>
                                <Input value={email}
                                       type="email"
                                       onChange={onEmailChange}
                                       onKeyDown={(e) => handleKeyDown(e, onInitiateSignIn)}/>
                            </Field>

                            <Field label={"Password"}
                                   validationState={"none"}
                                   validationMessage={""}>
                                <Input type="password"
                                       value={password}
                                       onChange={onPasswordChange}
                                       onKeyDown={(e) => handleKeyDown(e, onInitiateSignIn)}/>
                            </Field>

                            {signInInitiationSuccessful && renderOtpSection()}

                            {!signInInitiationSuccessful && renderInitiateSignInButton()}

                            {signInInitiationSuccessful && renderCompleteSignInButton()}

                            <div className={signInStyles.authNoAccount}>

                                <Caption1> Forgot your sign in credentials? &nbsp;
                                    <Link onClick={() => navigate("/account-recovery")}
                                          disabled={signInInitiating || signInCompleting}>
                                        <Text weight="semibold">Recover account</Text>
                                    </Link>
                                </Caption1>

                                <Divider> OR </Divider>

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
                    <section className={authorizationStyles.authSection2}>
                        <SignInCarousel/>
                    </section>
                </section>
            </section>
        }/>
    );
};

export default SignIn;