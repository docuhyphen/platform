import {ChangeEvent, useEffect, useRef, useState} from 'react';
import {InputOnChangeData} from "@fluentui/react-components";
import {useNavigate, useSearchParams} from 'react-router-dom';
import validator from 'validator';
import {
    completeSignIn,
    createSignInEmailFallbackChallenge,
    initiateSignIn,
    lookupSignInMethod,
    regenerateSignInOtp
} from '../../../services/authApi.ts';
import {fetchAppUser, fetchAppUserPersonOrganization} from '../../../services/appUserApi.ts';
import {useAuth} from '../../../context/AuthContext.tsx';
import {setApiClientAuthToken} from '../../../services/apiClient.ts';
import {AppUserDetailedDto, MfaMethod, ResponseError} from "../../models/models.tsx";
import {getOtpFriendlyMessage, normalizeApiError} from "../../../utils/apiErrorUtils.ts";
import {resolveOAuthErrorMessage} from "../../../utils/oauthErrorUtils.ts";

const SIGN_IN_EXCHANGE_DURATION_MS = 30 * 60 * 1000;
const RESEND_COOLDOWN_SECONDS = 30;

export type SignInStep = 'EMAIL_ENTRY' | 'PASSWORD_ENTRY' | 'MFA_ENTRY';

export type SignInInputChangeHandler = (event: ChangeEvent<HTMLInputElement>, data: InputOnChangeData) => void;

export interface SignInFlow
{
    step: SignInStep;
    email: string;
    password: string;
    otp: string;
    mfaMethod: MfaMethod;
    emailFallbackEnabled: boolean;
    lookingUp: boolean;
    signInInitiating: boolean;
    signInCompleting: boolean;
    resendingOtp: boolean;
    resendCooldownRemaining: number;
    sessionExpired: boolean;
    signInInitiationSuccessfulMsg: string;
    resetOtpResponseMessage: string;
    responseErrorMessage?: string;
    onEmailChange: SignInInputChangeHandler;
    onPasswordChange: SignInInputChangeHandler;
    onOtpChange: SignInInputChangeHandler;
    onLookupEmail: () => Promise<void>;
    onInitiateSignIn: () => Promise<void>;
    onCompleteSignIn: () => Promise<void>;
    onResendOtp: () => Promise<void>;
    onUseEmailFallback: () => Promise<void>;
    onResetSignIn: () => void;
    onDismissError: () => void;
}

export const useSignInFlow = (): SignInFlow =>
{
    const [step, setStep] = useState<SignInStep>('EMAIL_ENTRY');
    const [email, setEmail] = useState<string>('');
    const [otp, setOtp] = useState<string>('');
    const [mfaSessionId, setMfaSessionId] = useState<string>('');
    const [mfaMethod, setMfaMethod] = useState<MfaMethod>('EMAIL');
    const [emailFallbackEnabled, setEmailFallbackEnabled] = useState(false);
    const [password, setPassword] = useState<string>('');
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
    const [searchParams, setSearchParams] = useSearchParams();

    const onEmailChange: SignInInputChangeHandler = (_event, data) => setEmail((data.value || '').trim());
    const onOtpChange: SignInInputChangeHandler = (_event, data) => setOtp((data.value || '').trim());
    const onPasswordChange: SignInInputChangeHandler = (_event, data) => setPassword(data.value || '');

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
            const response = await initiateSignIn({email, password});

            setMfaSessionId(response?.mfaSessionId);
            setMfaMethod(response?.mfaType || 'EMAIL');
            setEmailFallbackEnabled(response?.emailFallbackEnabled === true);
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
            if (signInError?.reasonCode === 'SIGN_UP_REQUIRED')
            {
                const prefillEmail = encodeURIComponent(email || '');
                navigate(`/sign-up?email=${prefillEmail}`);
                return;
            }
            setResponseErrorMessage(getOtpFriendlyMessage(normalizeApiError(signInError, "An unknown error occurred signing in.")));
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
            setResponseErrorMessage("Verification code is required.");
            return;
        }

        setResponseErrorMessage(undefined);
        setSignInCompleting(true);

        try
        {
            const response = await completeSignIn({email, otp, mfaSessionId});
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
                navigate("/onboarding/individual");
            }
            catch
            {
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

    const onUseEmailFallback = async () =>
    {
        if (resendingOtp || sessionExpired) return;
        setResponseErrorMessage(undefined);
        setResendingOtp(true);
        try
        {
            const response = await createSignInEmailFallbackChallenge(email, mfaSessionId);
            setMfaMethod('EMAIL');
            setOtp('');
            setSignInInitiationSuccessfulMsg('');
            setResetOtpResponseMessage(response.message || 'A verification code has been sent to your email.');
            startResendCooldown();
        }
        catch (error)
        {
            setResponseErrorMessage(getOtpFriendlyMessage(normalizeApiError(error, "Could not send an email verification code.")));
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
            setResendCooldownRemaining((previous) =>
            {
                if (previous <= 1)
                {
                    if (cooldownTimerRef.current)
                    {
                        clearInterval(cooldownTimerRef.current);
                        cooldownTimerRef.current = null;
                    }
                    return 0;
                }
                return previous - 1;
            });
        }, 1000);
    };

    useEffect(() => () =>
    {
        if (sessionTimerRef.current) clearTimeout(sessionTimerRef.current);
        if (cooldownTimerRef.current) clearInterval(cooldownTimerRef.current);
    }, []);

    useEffect(() =>
    {
        const oauthErrorMessage = resolveOAuthErrorMessage(searchParams.get('errorCode'));

        if (!oauthErrorMessage)
        {
            return;
        }

        setResponseErrorMessage(oauthErrorMessage);
        const nextParams = new URLSearchParams(searchParams);
        nextParams.delete('errorCode');
        setSearchParams(nextParams, {replace: true});
    }, [searchParams, setSearchParams]);

    const onResetSignIn = () =>
    {
        setEmail('');
        setOtp('');
        setMfaSessionId('');
        setMfaMethod('EMAIL');
        setEmailFallbackEnabled(false);
        setPassword('');
        setSignInInitiating(false);
        setSignInCompleting(false);
        setResendingOtp(false);
        setResetOtpResponseMessage('');
        setSignInInitiationSuccessfulMsg('');
        setResponseErrorMessage(undefined);
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
    };

    return {
        step,
        email,
        password,
        otp,
        mfaMethod,
        emailFallbackEnabled,
        lookingUp,
        signInInitiating,
        signInCompleting,
        resendingOtp,
        resendCooldownRemaining,
        sessionExpired,
        signInInitiationSuccessfulMsg,
        resetOtpResponseMessage,
        responseErrorMessage,
        onEmailChange,
        onPasswordChange,
        onOtpChange,
        onLookupEmail,
        onInitiateSignIn,
        onCompleteSignIn,
        onResendOtp,
        onUseEmailFallback,
        onResetSignIn,
        onDismissError: () => setResponseErrorMessage(undefined),
    };
};
