import {ChangeEvent, useState} from 'react';
import {useSearchParams} from "react-router-dom";
import {InputOnChangeData} from "@fluentui/react-components";
import validator from 'validator';
import {completeSignUp, initiateSignUp, regenerateSignUpOtp} from "../../../services/authApi.ts";
import {getOtpFriendlyMessage, normalizeApiError} from "../../../utils/apiErrorUtils.ts";

export interface SignUpFormData
{
    email: string;
    otp: string;
    password: string;
    confirmationPassword: string;
}

export interface SignUpState
{
    formData: SignUpFormData;
    initiationSuccessful: boolean;
    initiationSuccessfulMsg?: string;
    otpRegenerationSuccessfulMsg?: string;
    otpRegenerationFailedMsg?: string;
    responseErrorMessage?: string;
    signUpSuccessful: boolean;
    initiatingSignUp: boolean;
    regeneratingOtp: boolean;
    completingSignUp: boolean;
    handleChange: (event: ChangeEvent<HTMLInputElement>, data: InputOnChangeData) => void;
    onInitiateSignUp: () => Promise<void>;
    onCompleteSignUp: () => Promise<void>;
    onRegenerateOTP: () => Promise<void>;
    onDismissError: () => void;
}

export const useSignUp = (): SignUpState =>
{
    const [searchParams] = useSearchParams();
    const prefillEmail = searchParams.get('email') || '';

    const [formData, setFormData] = useState<SignUpFormData>({
        email: prefillEmail,
        otp: '',
        password: '',
        confirmationPassword: ''
    });

    const [initiationSuccessful, setInitiationSuccessful] = useState(false);
    const [initiationSuccessfulMsg, setInitiationSuccessfulMsg] = useState<string>();
    const [otpRegenerationSuccessfulMsg, setOtpRegenerationSuccessfulMsg] = useState<string | undefined>('');
    const [otpRegenerationFailedMsg, setOtpRegenerationFailedMsg] = useState<string | undefined>('');
    const [responseErrorMessage, setFormErrorMessage] = useState<string | undefined>('');
    const [signUpSuccessful, setSignUpSuccessful] = useState(false);
    const [initiatingSignUp, setInitiatingSignUp] = useState(false);
    const [regeneratingOtp, setRegeneratingOtp] = useState(false);
    const [completingSignUp, setCompletingSignUp] = useState(false);

    const handleChange = (e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setFormErrorMessage('');

        setFormData({
            ...formData,
            [e.target.name]: (newValue.value || '')
        });
    };

    const onInitiateSignUp = async () =>
    {
        if (!validator.isEmail(formData.email))
        {
            setFormErrorMessage("A valid email is required");
            return;
        }

        if (initiatingSignUp) return;

        setInitiationSuccessfulMsg("");
        setFormErrorMessage('');
        setInitiatingSignUp(true);

        try
        {
            const response = await initiateSignUp({email: formData.email});
            setInitiationSuccessful(true);
            setInitiationSuccessfulMsg(response?.message);
        }
        catch (error)
        {
            setInitiationSuccessful(false);
            setFormErrorMessage(getOtpFriendlyMessage(normalizeApiError(error, "Could not start account creation. Please try again.")));
        }
        finally
        {
            setInitiatingSignUp(false);
        }
    };

    const isSignUpCompletionFormValid = () =>
    {
        if (!formData.otp)
        {
            setFormErrorMessage("Verification code is required");
            return false;
        }

        if (!formData.password)
        {
            setFormErrorMessage("Password is required");
            return false;
        }

        if (!formData.confirmationPassword)
        {
            setFormErrorMessage("Password confirmation is required");
            return false;
        }

        if (formData.password != formData.confirmationPassword)
        {
            setFormErrorMessage("Passwords do not match");
            return false;
        }

        return true;
    };

    const onCompleteSignUp = async () =>
    {
        if (!isSignUpCompletionFormValid())
        {
            return;
        }

        if (completingSignUp)
        {
            return;
        }

        setFormErrorMessage('');
        setCompletingSignUp(true);

        try
        {
            await completeSignUp(formData);
            setSignUpSuccessful(true);
        }
        catch (error)
        {
            setFormErrorMessage(getOtpFriendlyMessage(normalizeApiError(error, "Could not complete account creation. Please try again.")));
        }
        finally
        {
            setCompletingSignUp(false);
        }
    };

    const onRegenerateOTP = async () =>
    {
        if (regeneratingOtp) return;

        setFormData({...formData, otp: ''});
        setOtpRegenerationSuccessfulMsg('');
        setOtpRegenerationFailedMsg('');
        setRegeneratingOtp(true);
        setFormErrorMessage("");

        try
        {
            const response = await regenerateSignUpOtp({email: formData.email});
            setOtpRegenerationSuccessfulMsg(response?.message);
        }
        catch (error)
        {
            setOtpRegenerationFailedMsg(getOtpFriendlyMessage(normalizeApiError(error, "Could not resend the verification code. Please try again.")));
        }
        finally
        {
            setRegeneratingOtp(false);
        }
    };

    return {
        formData,
        initiationSuccessful,
        initiationSuccessfulMsg,
        otpRegenerationSuccessfulMsg,
        otpRegenerationFailedMsg,
        responseErrorMessage,
        signUpSuccessful,
        initiatingSignUp,
        regeneratingOtp,
        completingSignUp,
        handleChange,
        onInitiateSignUp,
        onCompleteSignUp,
        onRegenerateOTP,
        onDismissError: () => setFormErrorMessage(undefined),
    };
};
