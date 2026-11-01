import {ChangeEvent, useEffect, useState} from 'react';
import {useSearchParams} from "react-router-dom";
import {InputOnChangeData} from "@fluentui/react-components";
import validator from 'validator';
import {completePasswordReset, initiatePasswordReset, regeneratePasswordResetOtp} from "../../../services/authApi.ts";
import {getOtpFriendlyMessage, normalizeApiError} from "../../../utils/apiErrorUtils.ts";

export interface AccountRecoveryFormData
{
    email: string;
    otp: string;
    password: string;
    confirmationPassword: string;
}

export interface AccountRecoveryState
{
    formData: AccountRecoveryFormData;
    pwdResetInitiationSuccessful: boolean;
    pwdResetSuccessfulMsg?: string;
    otpRegenerationSuccessfulMsg?: string;
    otpRegenerationFailedMsg?: string;
    responseErrorMessage?: string;
    pwdResetSuccessful: boolean;
    initiatingPwdReset: boolean;
    regeneratingOtp: boolean;
    completingPwdReset: boolean;
    handleChange: (event: ChangeEvent<HTMLInputElement>, data: InputOnChangeData) => void;
    onInitiatePasswordReset: () => Promise<void>;
    onCompletePasswordReset: () => Promise<void>;
    onRegenerateOTP: () => Promise<void>;
    onDismissError: () => void;
}

export const useAccountRecovery = (): AccountRecoveryState =>
{
    const [searchParams] = useSearchParams();
    const [formData, setFormData] = useState<AccountRecoveryFormData>({
        email: '',
        otp: '',
        password: '',
        confirmationPassword: ''
    });
    const [pwdResetInitiationSuccessful, setPwdResetInitiationSuccessful] = useState(false);
    const [pwdResetSuccessfulMsg, setPwdResetSuccessfulMsg] = useState<string>();
    const [otpRegenerationSuccessfulMsg, setOtpRegenerationSuccessfulMsg] = useState<string | undefined>('');
    const [otpRegenerationFailedMsg, setOtpRegenerationFailedMsg] = useState<string | undefined>('');
    const [responseErrorMessage, setResponseError] = useState<string | undefined>('');
    const [pwdResetSuccessful, setPwdResetSuccessful] = useState(false);
    const [initiatingPwdReset, setInitiatingPwdReset] = useState(false);
    const [regeneratingOtp, setRegeneratingOtp] = useState(false);
    const [completingPwdReset, setCompletingPwdReset] = useState(false);

    useEffect(() =>
    {
        const prefillEmail = searchParams.get('email')?.trim();
        const reason = searchParams.get('reason');

        if (prefillEmail)
        {
            setFormData((prev) => ({
                ...prev,
                email: prefillEmail,
            }));
        }

        if (reason === 'PASSWORD_CHANGE_REQUIRED')
        {
            setResponseError('You must change your temporary password before signing in. Start account recovery below.');
        }
        else if (reason === 'TEMP_PASSWORD_EXPIRED')
        {
            setResponseError('Your temporary password has expired. Start account recovery below to set a new password.');
        }
    }, [searchParams]);

    const handleChange = (e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setFormData({
            ...formData,
            [e.target.name]: newValue.value || ''
        });
    };

    const onInitiatePasswordReset = async () =>
    {
        if (!validator.isEmail(formData.email))
        {
            setPwdResetInitiationSuccessful(false);
            setResponseError("Please enter a valid email address");
            return;
        }

        if (initiatingPwdReset) return;

        setPwdResetSuccessfulMsg("");
        setResponseError('');
        setInitiatingPwdReset(true);

        try
        {
            await initiatePasswordReset({email: formData.email});
            setPwdResetInitiationSuccessful(true);
            setPwdResetSuccessfulMsg("If you have an account with us, a verification code has been sent to your email address.");
        }
        catch (error)
        {
            setPwdResetInitiationSuccessful(false);
            setResponseError(getOtpFriendlyMessage(normalizeApiError(error, 'Could not start account recovery. Please try again.')));
        }
        finally
        {
            setInitiatingPwdReset(false);
        }
    };

    const onCompletePasswordReset = async () =>
    {
        if (completingPwdReset) return;

        if (!formData.otp)
        {
            setResponseError('Please enter the OTP sent to your email address');
            return;
        }

        if (!formData.password || !formData.password.length)
        {
            setResponseError('Please enter a new password');
            return;
        }

        if (!formData.confirmationPassword || !formData.confirmationPassword.length)
        {
            setResponseError('Please confirm your new password');
            return;
        }

        setPwdResetSuccessfulMsg('');
        setResponseError('');
        setCompletingPwdReset(true);

        try
        {
            await completePasswordReset(formData);
            setPwdResetSuccessful(true);
        }
        catch (error)
        {
            setResponseError(getOtpFriendlyMessage(normalizeApiError(error, 'Could not reset your password. Please try again.')));
        }
        finally
        {
            setCompletingPwdReset(false);
        }
    };

    const onRegenerateOTP = async () =>
    {
        if (regeneratingOtp) return;

        setFormData({...formData, otp: ''});
        setOtpRegenerationSuccessfulMsg('');
        setOtpRegenerationFailedMsg('');
        setRegeneratingOtp(true);

        try
        {
            const response = await regeneratePasswordResetOtp({email: formData.email});
            setOtpRegenerationSuccessfulMsg(response?.message);
        }
        catch (error)
        {
            setOtpRegenerationFailedMsg(getOtpFriendlyMessage(normalizeApiError(error, 'Could not resend the verification code. Please try again.')));
        }
        finally
        {
            setRegeneratingOtp(false);
        }
    };

    return {
        formData,
        pwdResetInitiationSuccessful,
        pwdResetSuccessfulMsg,
        otpRegenerationSuccessfulMsg,
        otpRegenerationFailedMsg,
        responseErrorMessage,
        pwdResetSuccessful,
        initiatingPwdReset,
        regeneratingOtp,
        completingPwdReset,
        handleChange,
        onInitiatePasswordReset,
        onCompletePasswordReset,
        onRegenerateOTP,
        onDismissError: () => setResponseError(undefined),
    };
};
