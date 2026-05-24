import React, {useState} from 'react';
import {completeSignIn, initiateSignIn, lookupSignInMethod, regenerateSignInOtp} from '../../../services/authApi.ts';
import {fetchAppUser, fetchAppUserPersonOrganization,} from '../../../services/appUserApi.ts';
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
import {AppUserDetailedDto, ResponseError, SignInLookupOrganizationOption} from "../../models/models.tsx";
import {setApiClientAuthToken} from '../../../services/apiClient.ts';
import {ArrowLeftRegular, DismissRegular} from "@fluentui/react-icons";
import AppLogo from "../../components/app-logo/AppLogo.tsx";
import SignInCarousel from "../carousel/SignInCarousel.tsx";
import {useSignInStyles} from "./SignInStyles.tsx";
import {useAuthorizationStyles} from "../AuthorizationStyles.tsx";
import {useGlobalStyles} from "../../../GlobalStyles.tsx";

type SignInStep = 'EMAIL_ENTRY' | 'ORG_PICKER' | 'PASSWORD_ENTRY' | 'MFA_ENTRY';

const SignIn: React.FC = () =>
{
    const [step, setStep] = useState<SignInStep>('EMAIL_ENTRY');
    const [email, setEmail] = useState<string>('');
    const [otp, setOtp] = useState<string>('');
    const [mfaSessionId, setMfaSessionId] = useState<string>('');
    const [password, setPassword] = useState<string>('');
    const [orgOptions, setOrgOptions] = useState<SignInLookupOrganizationOption[]>([]);
    const [lookingUp, setLookingUp] = useState<boolean>(false);
    const [signInInitiating, setSignInInitiating] = useState<boolean>(false);
    const [signInCompleting, setSignInCompleting] = useState<boolean>(false);
    const [resendingOtp, setResendingOtp] = useState<boolean>(false);
    const [resetOtpResponseMessage, setResetOtpResponseMessage] = useState<boolean>(false);
    const [signInInitiationSuccessfulMsg, setSignInInitiationSuccessfulMsg] = useState<string>('');
    const [responseErrorMessage, setResponseErrorMessage] = useState<string | undefined>('');
    const {setToken, setAccessToken, setIdToken, setAppUser, setAppUserPersonOrganization} = useAuth();
    const navigate = useNavigate();
    const signInStyles = useSignInStyles();
    const authorizationStyles = useAuthorizationStyles();
    const globalStyles = useGlobalStyles();

    const onEmailChange = (_e: React.ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) => setEmail(newValue.value || '');
    const onOtpChange = (_e: React.ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) => setOtp(newValue.value || '');
    const onPasswordChange = (_e: React.ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) => setPassword(newValue.value || '');

    const token = useToken();

    // Identifier-first lookup
    const onLookupEmail = async () =>
    {
        if (lookingUp) return;
        if (!email)
        {
            setResponseErrorMessage("Email is required.");
            return;
        }

        setLookingUp(true);
        setResponseErrorMessage(undefined);

        try
        {
            const response = await lookupSignInMethod({email});

            if (response.outcome === 'MULTIPLE_ORGS' && response.organizations && response.organizations.length > 0)
            {
                setOrgOptions(response.organizations);
                setStep('ORG_PICKER');
            }
            else if (response.redirectUrl)
            {
                window.location.href = response.redirectUrl;
            }
            else
            {
                setStep('PASSWORD_ENTRY');
            }
        }
        catch (error)
        {
            setResponseErrorMessage((error as ResponseError)?.errorMessage ?? "An error occurred.");
        }
        finally
        {
            setLookingUp(false);
        }
    };

    // Internal password + MFA initiation
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

            setMfaSessionId(response?.mfaSessionId);
            setSignInInitiationSuccessfulMsg(response?.message);
            setStep('MFA_ENTRY');
        }
        catch (error)
        {
            setResponseErrorMessage((error as ResponseError)?.errorMessage ?? "An unknown error occurred signing in.");
        }
        finally
        {
            setSignInInitiating(false);
        }
    };

    // Complete sign-in with MFA OTP
    const onCompleteSignIn = async () =>
    {
        if (signInCompleting) return;

        if (!otp)
        {
            setResponseErrorMessage("Verification code is required.");
            return;
        }

        setResponseErrorMessage(undefined);
        setSignInCompleting(true);

        try
        {
            const signInCompletionRequest = {email, otp, mfaSessionId};
            const response = await completeSignIn(signInCompletionRequest);

            // Use new token triple if available, fall back to legacy token
            const activeToken = response.accessToken;
            setAccessToken(activeToken);
            if (response.idToken) setIdToken(response.idToken);
            setApiClientAuthToken(activeToken);

            let appUser: AppUserDetailedDto | null = null;

            try
            {
                appUser = await fetchAppUser(activeToken);
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
                if (appUser && appUser.person)
                {
                    const organization = await fetchAppUserPersonOrganization(appUser.id, appUser.person?.id, activeToken?.toString());
                    setAppUserPersonOrganization(organization);
                    navigate("/sharing-sessions");
                    return;
                }
                else
                {
                    navigate("/onboarding/individual");
                    return;
                }
            }
            catch
            {
                // Navigate to sharing sessions even if org fetch fails
                navigate("/sharing-sessions");
            }
        }
        catch (error)
        {
            const errMsg = (error as ResponseError)?.errorMessage;
            setResponseErrorMessage(errMsg || "An unknown error occurred signing in.");
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
            const regenerateResponse = await regenerateSignInOtp({email, mfaSessionId});
            setResetOtpResponseMessage(regenerateResponse.message)
            setOtp("")
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

    const onSelectOrg = async (orgId: string) =>
    {
        setLookingUp(true);
        setResponseErrorMessage(undefined);
        try
        {
            const response = await lookupSignInMethod({email, orgId});
            if (response.redirectUrl)
            {
                window.location.href = response.redirectUrl;
            }
            else
            {
                setStep('PASSWORD_ENTRY');
            }
        }
        catch (error)
        {
            setResponseErrorMessage((error as ResponseError)?.errorMessage ?? "An error occurred.");
        }
        finally
        {
            setLookingUp(false);
        }
    };

    const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>, action: () => void) =>
    {
        if (event.key === 'Enter')
        {
            action();
        }
    };

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

    const onResetSignIn = () =>
    {
        setEmail('');
        setOtp('');
        setMfaSessionId('');
        setPassword('');
        setSignInInitiating(false);
        setSignInCompleting(false);
        setResendingOtp(false);
        setResetOtpResponseMessage(false);
        setSignInInitiationSuccessfulMsg('');
        setResponseErrorMessage(undefined);
        setOrgOptions([]);
        setStep('EMAIL_ENTRY');
    }

    return (
        <RedirectIfAuthenticated element={
            <section className={authorizationStyles.auth}>
                <section className={authorizationStyles.authSection}>
                    <section className={authorizationStyles.authSection1}>
                        <div>
                            <AppLogo/>
                        </div>
                        <div className={authorizationStyles.authorizationFormSection}>

                            <Subtitle1 align={"center"}>
                                {step !== 'EMAIL_ENTRY' &&
                                    <Button icon={<ArrowLeftRegular/>}
                                            appearance={"transparent"}
                                            onClick={() => onResetSignIn()}/>
                                }
                                Sign in
                            </Subtitle1>

                            {renderErrorMessage()}

                            {step === 'EMAIL_ENTRY' && (
                                <>
                                    <Field label={"Email"}
                                           validationState={"none"}
                                           validationMessage={""}>
                                        <Input value={email}
                                               type="email"
                                               onChange={onEmailChange}
                                               onKeyDown={(e) => handleKeyDown(e, onLookupEmail)}/>
                                    </Field>

                                    <Button onClick={onLookupEmail}
                                            appearance="primary"
                                            className={globalStyles.buttonWithLoading}
                                            shape={"circular"}>
                                        {lookingUp && <><Spinner size={"tiny"}/> Checking...</>}
                                        {!lookingUp && "Continue"}
                                    </Button>
                                </>
                            )}

                            {step === 'ORG_PICKER' && (
                                <>
                                    <Text size={300}>Multiple organizations are associated with <strong>{email}</strong>. Select yours to continue.</Text>
                                    <div style={{display: 'flex', flexDirection: 'column', gap: '8px', marginTop: '8px'}}>
                                        {orgOptions.map(org => (
                                            <Button
                                                key={org.id}
                                                appearance="outline"
                                                shape="circular"
                                                disabled={lookingUp}
                                                onClick={() => onSelectOrg(org.id)}
                                                style={{justifyContent: 'flex-start'}}
                                            >
                                                {lookingUp ? <Spinner size="tiny"/> : null}
                                                {org.name}
                                            </Button>
                                        ))}
                                    </div>
                                </>
                            )}

                            {step === 'PASSWORD_ENTRY' && (
                                <>
                                    <Field label={"Email"}>
                                        <Input value={email} type="email" disabled/>
                                    </Field>

                                    <Field label={"Password"}
                                           validationState={"none"}
                                           validationMessage={""}>
                                        <Input type="password"
                                               value={password}
                                               onChange={onPasswordChange}
                                               onKeyDown={(e) => handleKeyDown(e, onInitiateSignIn)}/>
                                    </Field>

                                    <Button onClick={onInitiateSignIn}
                                            appearance="primary"
                                            className={globalStyles.buttonWithLoading}
                                            shape={"circular"}>
                                        {signInInitiating && <><Spinner size={"tiny"}/> Signing in</>}
                                        {!signInInitiating && "Sign In"}
                                    </Button>
                                </>
                            )}

                            {step === 'MFA_ENTRY' && (
                                <>
                                    <span>{signInInitiationSuccessfulMsg}</span>

                                    <Field label={"Verification code"}
                                           validationState={"none"}
                                           validationMessage={""}
                                           hint={resetOtpResponseMessage ? `${resetOtpResponseMessage}` : "A verification code has been sent to your email"}>
                                        <Input value={otp}
                                               autoComplete="false"
                                               disabled={resendingOtp || signInCompleting}
                                               onChange={onOtpChange}
                                               onKeyDown={(e) => handleKeyDown(e, onCompleteSignIn)}/>
                                    </Field>

                                    <Button appearance="transparent"
                                            size={"small"}
                                            disabled={resendingOtp || signInCompleting}
                                            shape={"circular"}
                                            onClick={onResendOtp}
                                            className={globalStyles.buttonWithLoading}>
                                        <>
                                            {resendingOtp && <Spinner size={"tiny"}/>}
                                            Resend verification code
                                        </>
                                    </Button>

                                    <Button onClick={onCompleteSignIn}
                                            disabled={resendingOtp}
                                            appearance="primary"
                                            className={globalStyles.buttonWithLoading}
                                            shape={"circular"}>
                                        {signInCompleting && <><Spinner size={"tiny"}/> Verifying Code</>}
                                        {!signInCompleting && "Verify Code"}
                                    </Button>
                                </>
                            )}

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