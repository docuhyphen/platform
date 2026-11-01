import React, {useState} from "react";
import {ContactDetailsDetailedDto} from "../../models/models.tsx";
import {useAuth} from "../../../context/AuthContext.tsx";
import {
    completePhoneAddition,
    completePhoneUpdate,
    initiatePhoneAddition,
    initiatePhoneUpdate
} from "../../../services/contactDetailsApi";
import {extractPhoneManagementErrorMessage, validatePhone} from "./phoneManagementValidation.ts";

export enum PhoneManagementMode
{
    ADD,
    EDIT
}

interface UsePhoneManagementArgs
{
    contactDetails?: ContactDetailsDetailedDto;
    mode?: PhoneManagementMode;
    onDismiss: () => void;
    onComplete?: (contactDetails: ContactDetailsDetailedDto) => void;
}

type InitiateCall = (contactDetailsId: string, phoneNumber: string, token: string | null) => Promise<unknown>;
type CompleteCall = (contactDetailsId: string, phoneNumber: string, verificationCode: string, token: string | null) => Promise<unknown>;

export const usePhoneManagement = ({contactDetails, mode, onDismiss, onComplete}: UsePhoneManagementArgs) =>
{
    const {token} = useAuth();
    const [phoneNumber, setPhoneNumber] = useState<string | undefined>(contactDetails?.phoneNumber);
    const [processing, setProcessing] = useState(false);
    const [addOrEditInitiated, setAddOrEditInitiated] = useState(false);
    const [verificationCode, setVerificationCode] = useState('');
    const [error, setError] = useState<string | null>(null);

    const onPhoneNumberChange = (e: React.ChangeEvent<HTMLInputElement>) =>
    {
        setPhoneNumber(e.target.value);
    };

    const onVerificationCodeChange = (e: React.ChangeEvent<HTMLInputElement>) =>
    {
        setVerificationCode(e.target.value);
    };

    const resetDialog = () =>
    {
        setPhoneNumber(contactDetails?.phoneNumber);
        setVerificationCode('');
        setAddOrEditInitiated(false);
        setError(null);
        onDismiss();
    };

    const initiate = async (call: InitiateCall, failureLog: string) =>
    {
        if (processing || !contactDetails?.id) return;

        const validationError = validatePhone(phoneNumber);
        if (validationError)
        {
            setError(validationError);
            return;
        }

        setProcessing(true);
        setError(null);

        try
        {
            await call(contactDetails.id, phoneNumber!, token);
            setAddOrEditInitiated(true);
        }
        catch (e: unknown)
        {
            setError(extractPhoneManagementErrorMessage(e));
            console.error(failureLog, e);
        }
        finally
        {
            setProcessing(false);
        }
    };

    const complete = async (call: CompleteCall, failureLog: string) =>
    {
        if (processing || !contactDetails?.id) return;

        const validationError = validatePhone(phoneNumber);
        if (validationError)
        {
            setError(validationError);
            return;
        }

        if (!verificationCode.trim())
        {
            setError("Verification code is required");
            return;
        }

        setProcessing(true);
        setError(null);

        try
        {
            await call(contactDetails.id, phoneNumber!, verificationCode, token);

            const updatedContactDetails = {
                ...contactDetails,
                phoneNumber: phoneNumber
            } as ContactDetailsDetailedDto;

            if (onComplete)
            {
                onComplete(updatedContactDetails);
                resetDialog();
            }

            onDismiss();
        }
        catch (e: unknown)
        {
            setError(extractPhoneManagementErrorMessage(e));
            console.error(failureLog, e);
        }
        finally
        {
            setProcessing(false);
        }
    };

    const onAddOrUpdatePhoneNumber = () =>
    {
        if (addOrEditInitiated)
        {
            if (mode === PhoneManagementMode.ADD)
            {
                void complete(completePhoneAddition, "Failed to complete phone number addition:");
            }
            else
            {
                void complete(completePhoneUpdate, "Failed to complete phone number update:");
            }
        }
        else
        {
            if (mode === PhoneManagementMode.ADD)
            {
                void initiate(initiatePhoneAddition, "Failed to initiate phone number addition:");
            }
            else
            {
                void initiate(initiatePhoneUpdate, "Failed to initiate phone number update:");
            }
        }
    };

    return {
        phoneNumber,
        processing,
        addOrEditInitiated,
        verificationCode,
        error,
        onPhoneNumberChange,
        onVerificationCodeChange,
        onAddOrUpdatePhoneNumber,
        resetDialog,
    };
};
