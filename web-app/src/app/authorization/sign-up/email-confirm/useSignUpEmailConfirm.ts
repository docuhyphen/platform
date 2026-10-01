import {ChangeEvent, useEffect, useMemo, useState} from 'react';
import {useSearchParams} from 'react-router-dom';
import {InputOnChangeData} from "@fluentui/react-components";
import {checkSignUpEmailConfirmToken, confirmSignUpEmail, regenerateSignUpOtp} from "../../../../services/authApi.ts";
import {getOtpFriendlyMessage, normalizeApiError} from "../../../../utils/apiErrorUtils.ts";

const INVALID_LINK_REASON_CODE = 'SIGN_UP_LINK_INVALID';

export interface SignUpEmailConfirmState
{
    email: string;
    password: string;
    confirmationPassword: string;
    validatingToken: boolean;
    tokenError?: string;
    completingSignUp: boolean;
    signUpSuccessful: boolean;
    regeneratingOtp: boolean;
    otpRegenerationSuccessMsg?: string;
    otpRegenerationFailedMsg?: string;
    errorMessage?: string;
    onPasswordChange: (event: ChangeEvent<HTMLInputElement>, data: InputOnChangeData) => void;
    onCompleteSignUp: () => Promise<void>;
    onResendCode: () => Promise<void>;
    onDismissError: () => void;
}

export const useSignUpEmailConfirm = (): SignUpEmailConfirmState =>
{
    const [searchParams] = useSearchParams();
    const token = useMemo(() => (searchParams.get('token') || '').trim(), [searchParams]);

    const [validatingToken, setValidatingToken] = useState<boolean>(true);
    const [tokenError, setTokenError] = useState<string | undefined>();
    const [email, setEmail] = useState<string>('');
    const [password, setPassword] = useState('');
    const [confirmationPassword, setConfirmationPassword] = useState('');
    const [completingSignUp, setCompletingSignUp] = useState(false);
    const [signUpSuccessful, setSignUpSuccessful] = useState(false);
    const [regeneratingOtp, setRegeneratingOtp] = useState(false);
    const [otpRegenerationSuccessMsg, setOtpRegenerationSuccessMsg] = useState<string>();
    const [otpRegenerationFailedMsg, setOtpRegenerationFailedMsg] = useState<string>();
    const [errorMessage, setErrorMessage] = useState<string | undefined>();

    useEffect(() =>
    {
        let cancelled = false;

        const validate = async () =>
        {
            if (!token)
            {
                setTokenError("This verification link is missing required information.");
                setValidatingToken(false);
                return;
            }

            try
            {
                const response = await checkSignUpEmailConfirmToken(token);
                if (cancelled) return;
                setEmail(response.email);
            }
            catch (error)
            {
                if (cancelled) return;
                setTokenError(getOtpFriendlyMessage(normalizeApiError(error, "This verification link is invalid or has expired.")));
            }
            finally
            {
                if (!cancelled) setValidatingToken(false);
            }
        };

        void validate();
        return () =>
        {
            cancelled = true;
        };
    }, [token]);

    const onPasswordChange = (event: ChangeEvent<HTMLInputElement>, data: InputOnChangeData) =>
    {
        setErrorMessage(undefined);
        if (event.target.name === 'password')
        {
            setPassword(data.value || '');
        }
        else if (event.target.name === 'confirmationPassword')
        {
            setConfirmationPassword(data.value || '');
        }
    };

    const validateForm = (): boolean =>
    {
        if (!password)
        {
            setErrorMessage("Password is required");
            return false;
        }

        if (!confirmationPassword)
        {
            setErrorMessage("Password confirmation is required");
            return false;
        }

        if (password !== confirmationPassword)
        {
            setErrorMessage("Passwords do not match");
            return false;
        }

        return true;
    };

    const onCompleteSignUp = async () =>
    {
        if (!token || completingSignUp) return;
        if (!validateForm()) return;

        setErrorMessage(undefined);
        setCompletingSignUp(true);

        try
        {
            await confirmSignUpEmail({token, password, confirmationPassword});
            setSignUpSuccessful(true);
        }
        catch (error)
        {
            const apiError = normalizeApiError(error, "We couldn't complete sign up. Please try again.");
            const message = getOtpFriendlyMessage(apiError);
            setErrorMessage(message);

            if (apiError.reasonCode === INVALID_LINK_REASON_CODE)
            {
                setTokenError(message);
            }
        }
        finally
        {
            setCompletingSignUp(false);
        }
    };

    const onResendCode = async () =>
    {
        if (!email || regeneratingOtp) return;

        setOtpRegenerationSuccessMsg(undefined);
        setOtpRegenerationFailedMsg(undefined);
        setErrorMessage(undefined);
        setRegeneratingOtp(true);

        try
        {
            const response = await regenerateSignUpOtp({email});
            setOtpRegenerationSuccessMsg(
                response?.message
                || "We've sent a new verification email. Open it and click the new link to continue."
            );
        }
        catch (error)
        {
            setOtpRegenerationFailedMsg(getOtpFriendlyMessage(normalizeApiError(error, "Failed to send a new verification email.")));
        }
        finally
        {
            setRegeneratingOtp(false);
        }
    };

    return {
        email,
        password,
        confirmationPassword,
        validatingToken,
        tokenError,
        completingSignUp,
        signUpSuccessful,
        regeneratingOtp,
        otpRegenerationSuccessMsg,
        otpRegenerationFailedMsg,
        errorMessage,
        onPasswordChange,
        onCompleteSignUp,
        onResendCode,
        onDismissError: () => setErrorMessage(undefined),
    };
};
