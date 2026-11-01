import React, {useState} from "react";
import {ContactDetailsDetailedDto} from "../../models/models.tsx";
import {useAuth} from "../../../context/AuthContext.tsx";
import {
    completeEmailAddition,
    completeEmailUpdate,
    initiateEmailAddition,
    initiateEmailUpdate
} from "../../../services/contactDetailsApi";
import {extractEmailManagementErrorMessage, validateEmail} from "./emailManagementValidation.ts";

export enum EmailManagementMode
{
    ADD,
    EDIT
}

interface UseEmailManagementArgs
{
    contactDetails?: ContactDetailsDetailedDto;
    mode?: EmailManagementMode;
    onDismiss: () => void;
    onComplete?: (contactDetails: ContactDetailsDetailedDto) => void;
}

type InitiateCall = (contactDetailsId: string, email: string, token: string | null) => Promise<unknown>;
type CompleteCall = (contactDetailsId: string, email: string, verificationCode: string, token: string | null) => Promise<unknown>;

export const useEmailManagement = ({contactDetails, mode, onDismiss, onComplete}: UseEmailManagementArgs) =>
{
    const {token} = useAuth();
    const [email, setEmail] = useState<string | undefined>(contactDetails?.email);
    const [processing, setProcessing] = useState(false);
    const [addOrEditInitiated, setAddOrEditInitiated] = useState(false);
    const [verificationCode, setVerificationCode] = useState('');
    const [error, setError] = useState<string | null>(null);

    const onEmailChange = (e: React.ChangeEvent<HTMLInputElement>) =>
    {
        setEmail(e.target.value);
    };

    const onVerificationCodeChange = (e: React.ChangeEvent<HTMLInputElement>) =>
    {
        setVerificationCode(e.target.value);
    };

    const resetDialog = () =>
    {
        setEmail(contactDetails?.email);
        setVerificationCode('');
        setAddOrEditInitiated(false);
        setError(null);
        onDismiss();
    };

    const initiate = async (call: InitiateCall, failureLog: string) =>
    {
        if (processing || !contactDetails?.id) return;

        const validationError = validateEmail(email);
        if (validationError)
        {
            setError(validationError);
            return;
        }

        setProcessing(true);
        setError(null);

        try
        {
            await call(contactDetails.id, email!, token);
            setAddOrEditInitiated(true);
        }
        catch (e: unknown)
        {
            setError(extractEmailManagementErrorMessage(e));
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

        const validationError = validateEmail(email);
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
            await call(contactDetails.id, email!, verificationCode, token);

            const updatedContactDetails = {
                ...contactDetails,
                email: email
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
            setError(extractEmailManagementErrorMessage(e));
            console.error(failureLog, e);
        }
        finally
        {
            setProcessing(false);
        }
    };

    const onAddOrUpdateEmail = () =>
    {
        if (addOrEditInitiated)
        {
            if (mode === EmailManagementMode.ADD)
            {
                void complete(completeEmailAddition, "Failed to complete email addition:");
            }
            else
            {
                void complete(completeEmailUpdate, "Failed to complete email update:");
            }
        }
        else
        {
            if (mode === EmailManagementMode.ADD)
            {
                void initiate(initiateEmailAddition, "Failed to initiate email addition:");
            }
            else
            {
                void initiate(initiateEmailUpdate, "Failed to initiate email update:");
            }
        }
    };

    return {
        email,
        processing,
        addOrEditInitiated,
        verificationCode,
        error,
        onEmailChange,
        onVerificationCodeChange,
        onAddOrUpdateEmail,
        resetDialog,
    };
};
