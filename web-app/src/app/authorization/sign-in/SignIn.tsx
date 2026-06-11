import React, {useEffect, useRef, useState} from 'react';
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
import validator from 'validator';
import {getOtpFriendlyMessage, normalizeApiError} from "../../../utils/apiErrorUtils.ts";

const SIGN_IN_EXCHANGE_DURATION_MS = 30 * 60 * 1000; // 30 minutes
const RESEND_COOLDOWN_SECONDS = 30;

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
    const [resetOtpResponseMessage, setResetOtpResponseMessage] = useState<string>('');
    const [signInInitiationSuccessfulMsg, setSignInInitiationSuccessfulMsg] = useState<string>('');
    const [responseErrorMessage, setResponseErrorMessage] = useState<string | undefined>('');
    const [resendCooldownRemaining, setResendCooldownRemaining] = useState<number>(0);
    const [sessionExpired, setSessionExpired] = useState<boolean>(false);
    const sessionTimerRef = useRef<number | null>(null);
    const cooldownTimerRef = useRef<number | null>(null);
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
        if (!validator.isEmail(email))
        {
            setResponseErrorMessage("Please enter a valid email address.");
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
            setResponseErrorMessage(getOtpFriendlyMessage(normalizeApiError(error, "An error occurred.")));
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
            startSessionTimer();
            startResendCooldown();
        }
        catch (error)
        {
            const signInError = (error as ResponseError);
            if (signInError?.reasonCode === 'PASSWORD_CHANGE_REQUIRED' || signInError?.reasonCode === 'TEMP_PASSWORD_EXPIRED')
            {
                const prefillEmail = encodeURIComponent(email || '');
                navigate(`/account-recovery?email=${prefillEmail}&reason=${signInError.reasonCode}`);
                return;
            }
            setResponseErrorMessage(getOtpFriendlyMessage(normalizeApiError(signInError, "An unknown error occurred signing in.")));
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
                setResponseErrorMessage(getOtpFriendlyMessage(normalizeApiError(error, "Could not load your account. Please try again.")));
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
                    navigate("/exchanges");
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
                // Navigate to exchanges even if org fetch fails
                navigate("/exchanges");
            }
        }
        catch (error)
        {
            setResponseErrorMessage(getOtpFriendlyMessage(normalizeApiError(error, "An unknown error occurred signing in.")));
        }
        finally
        {
            setSignInCompleting(false);
        }
    };

    const onResendOtp = async () =>
    {
        if (resendingOtp || resendCooldownRemaining > 0 || sessionExpired) return;

        setResponseErrorMessage(undefined);
        setResetOtpResponseMessage('');
        setResendingOtp(true);

        try
        {
            const regenerateResponse = await regenerateSignInOtp({email, mfaSessionId});
            setResetOtpResponseMessage(regenerateResponse.message || "A new verification code has been sent to your email.");
            setOtp("");
            startResendCooldown();
        }
        catch (error)
        {
            setResponseErrorMessage(getOtpFriendlyMessage(normalizeApiError(error, "Could not resend the verification code. Please try again.")));
        }
        finally
        {
            setResendingOtp(false);
        }
    };

    const startSessionTimer = () =>
    {
        if (sessionTimerRef.current)
        {
            clearTimeout(sessionTimerRef.current);
        }
        setSessionExpired(false);
        sessionTimerRef.current = window.setTimeout(() =>
        {
            setSessionExpired(true);
        }, SIGN_IN_EXCHANGE_DURATION_MS);
    };

    const startResendCooldown = () =>
    {
        if (cooldownTimerRef.current)
        {
            clearInterval(cooldownTimerRef.current);
        }
        setResendCooldownRemaining(RESEND_COOLDOWN_SECONDS);
        cooldownTimerRef.current = window.setInterval(() =>
        {
            setResendCooldownRemaining((prev) =>
            {
                if (prev <= 1)
                {
                    if (cooldownTimerRef.current)
                    {
                        clearInterval(cooldownTimerRef.current);
                        cooldownTimerRef.current = null;
                    }
                    return 0;
                }
                return prev - 1;
            });
        }, 1000);
    };

    useEffect(() => () =>
    {
        if (sessionTimerRef.current) clearTimeout(sessionTimerRef.current);
        if (cooldownTimerRef.current) clearInterval(cooldownTimerRef.current);
    }, []);

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
            setResponseErrorMessage(getOtpFriendlyMessage(normalizeApiError(error, "An error occurred.")));
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
        setResetOtpResponseMessage('');
        setSignInInitiationSuccessfulMsg('');
        setResponseErrorMessage(undefined);
        setOrgOptions([]);
        setStep('EMAIL_ENTRY');
        setSessionExpired(false);
        setResendCooldownRemaining(0);
        if (sessionTimerRef.current)
        {
            clearTimeout(sessionTimerRef.current);
            sessionTimerRef.current = null;
        }
        if (cooldownTimerRef.current)
        {
            clearInterval(cooldownTimerRef.current);
            cooldownTimerRef.current = null;
        }
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
                                               maxLength={254}
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
                                               maxLength={30}
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

                                    {sessionExpired && (
                                        <MessageBar intent="warning">
                                            <MessageBarBody>
                                                Your sign-in session has expired. Please sign in again.
                                            </MessageBarBody>
                                        </MessageBar>
                                    )}

                                    <Field label={"Verification code"}
                                           validationState={resetOtpResponseMessage ? "success" : "none"}
                                           validationMessage={resetOtpResponseMessage}
                                           hint={resetOtpResponseMessage ? undefined : "A verification code has been sent to your email"}>
                                        <Input value={otp}
                                               maxLength={6}
                                               autoComplete="false"
                                               disabled={resendingOtp || signInCompleting || sessionExpired}
                                               onChange={onOtpChange}
                                               onKeyDown={(e) => handleKeyDown(e, onCompleteSignIn)}/>
                                    </Field>

                                    <Button appearance="transparent"
                                            size={"small"}
                                            disabled={resendingOtp || signInCompleting || sessionExpired || resendCooldownRemaining > 0}
                                            shape={"circular"}
                                            onClick={onResendOtp}
                                            className={globalStyles.buttonWithLoading}>
                                        <>
                                            {resendingOtp && <Spinner size={"tiny"}/>}
                                            {resendCooldownRemaining > 0
                                                ? `Resend verification code (${resendCooldownRemaining}s)`
                                                : "Resend verification code"}
                                        </>
                                    </Button>

                                    {!sessionExpired && (
                                        <Button onClick={onCompleteSignIn}
                                                disabled={resendingOtp}
                                                appearance="primary"
                                                className={globalStyles.buttonWithLoading}
                                                shape={"circular"}>
                                            {signInCompleting && <><Spinner size={"tiny"}/> Verifying Code</>}
                                            {!signInCompleting && "Verify Code"}
                                        </Button>
                                    )}

                                    {sessionExpired && (
                                        <Button onClick={onResetSignIn}
                                                appearance="primary"
                                                shape={"circular"}>
                                            Start over
                                        </Button>
                                    )}
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