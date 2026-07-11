import React, {useState} from "react";
import {ContactDetailsDetailedDto} from "../../models/models.tsx";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Field,
    Input,
    Spinner
} from "@fluentui/react-components";
import {useAuth} from "../../../context/AuthContext.tsx";
import {
    completeEmailAddition,
    completeEmailUpdate,
    initiateEmailAddition,
    initiateEmailUpdate
} from "../../../services/contactDetailsApi";
import {useEmailManagementDialogStyles} from "./EmailManagementDialogStyles.tsx";
import {useGlobalStyles} from "../../../GlobalStyles.tsx";
import {isValidEmail} from "../../../utils/helpers.ts";

export enum EmailManagementMode
{
    ADD,
    EDIT
}

interface EmailManagementDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    contactDetails?: ContactDetailsDetailedDto;
    onComplete?: (contactDetails: ContactDetailsDetailedDto) => void;
    mode?: EmailManagementMode;
}

const EmailManagementDialog: React.FC<EmailManagementDialogProps> = (
    {
        contactDetails,
        mode,
        onDismiss,
        isOpen,
        onComplete
    }) =>
{
    const styles = useEmailManagementDialogStyles()
    const globalStyles = useGlobalStyles()
    const {token} = useAuth();

    const extractErrorMessage = (e: unknown): string =>
    {
        if (typeof e === "string") return e;
        const error = e as { errorMessage?: string; message?: string; response?: { data?: { errorMessage?: string; message?: string } } };
        const msg = error.errorMessage || error.message || error.response?.data?.errorMessage || error.response?.data?.message;
        if (msg) return msg;
        return "Something went wrong. Please try again in a moment.";
    }

    const validateEmail = (value: string | undefined): string | null =>
    {
        if (!value || !value.trim()) return "Email is required";
        if (!isValidEmail(value.trim())) return "Please enter a valid email address";
        return null;
    }
    const [email, setEmail] = useState<string | undefined>(contactDetails?.email);
    const [processing, setProcessing] = useState(false);
    const [addOrEditInitiated, setAddOrEditInitiated] = useState(false);
    const [verificationCode, setVerificationCode] = useState('');
    const [error, setError] = useState<string | null>(null);

    const onEmailChange = (e: React.ChangeEvent<HTMLInputElement>) =>
    {
        setEmail(e.target.value);
    }

    const onVerificationCodeChange = (e: React.ChangeEvent<HTMLInputElement>) =>
    {
        setVerificationCode(e.target.value);
    }

    const onInitiateAddEmail = async () =>
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
            await initiateEmailAddition(contactDetails.id, email, token);
            setAddOrEditInitiated(true);
        }
        catch (e: unknown)
        {
            setError(extractErrorMessage(e));
            console.error("Failed to initiate email addition:", e);
        }
        finally
        {
            setProcessing(false);
        }
    }

    const onCompleteAddEmail = async () =>
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
            await completeEmailAddition(contactDetails.id, email!, verificationCode, token);

            const updatedContactDetails = {
                ...contactDetails,
                email: email
            } as ContactDetailsDetailedDto;

            if (onComplete)
            {
                onComplete(updatedContactDetails);
                resetDialog()
            }

            onDismiss();
        }
        catch (e: unknown)
        {
            setError(extractErrorMessage(e));
            console.error("Failed to complete email addition:", e);
        }
        finally
        {
            setProcessing(false);
        }
    }

    const onInitiateEditEmail = async () =>
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
            await initiateEmailUpdate(contactDetails.id, email, token);
            setAddOrEditInitiated(true);
        }
        catch (e: unknown)
        {
            setError(extractErrorMessage(e));
            console.error("Failed to initiate email update:", e);
        }
        finally
        {
            setProcessing(false);
        }
    }

    const onCompleteEditEmail = async () =>
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
            await completeEmailUpdate(contactDetails.id, email!, verificationCode, token);

            const updatedContactDetails = {
                ...contactDetails,
                email: email
            } as ContactDetailsDetailedDto;

            if (onComplete)
            {
                onComplete(updatedContactDetails);
                resetDialog()
            }

            onDismiss();
        }
        catch (e: unknown)
        {
            setError(extractErrorMessage(e));
            console.error("Failed to complete email update:", e);
        }
        finally
        {
            setProcessing(false);
        }
    }

    const onAddOrUpdateEmail = () =>
    {
        if (addOrEditInitiated)
        {
            if (mode === EmailManagementMode.ADD)
            {
                onCompleteAddEmail();
            }
            else
            {
                onCompleteEditEmail();
            }
        }
        else
        {
            if (mode === EmailManagementMode.ADD)
            {
                onInitiateAddEmail();
            }
            else
            {
                onInitiateEditEmail();
            }
        }
    }

    const resetDialog = () =>
    {
        setEmail(contactDetails?.email);
        setVerificationCode('');
        setAddOrEditInitiated(false);
        setError(null);
        onDismiss();
    }

    return (
        <Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>
                        {mode === EmailManagementMode.ADD ? "Add new email" : "Edit email"}
                    </DialogTitle>
                    <DialogContent className={styles.dialogContentContainer}>
                        { error && <div className={styles.errorContainer}>{error}</div>}

                        <Field label="Email">
                            <Input
                                id={"email-management-email-input"}
                                type="email"
                                value={email || ''}
                                onChange={onEmailChange}
                                onKeyDown={(e) => { if (e.key === "Enter") onAddOrUpdateEmail(); }}
                                maxLength={254}
                                disabled={addOrEditInitiated || processing}
                            />
                        </Field>

                        {addOrEditInitiated && (
                            <Field label="Verification code">
                                <Input
                                    id={"email-management-verification-input"}
                                    type="text"
                                    value={verificationCode}
                                    onChange={onVerificationCodeChange}
                                    onKeyDown={(e) => { if (e.key === "Enter") onAddOrUpdateEmail(); }}
                                    maxLength={10}
                                />
                            </Field>
                        )}
                    </DialogContent>
                </DialogBody>
                <DialogActions>
                    <Button
                        id={"email-management-submit-btn"}
                        appearance="primary"
                        shape="circular"
                        className={globalStyles.buttonWithLoading}
                        disabled={processing}
                        onClick={onAddOrUpdateEmail}
                    >
                        {processing && <Spinner size="tiny"/>}
                        {addOrEditInitiated ? "Verify" : (mode === EmailManagementMode.ADD ? "Add" : "Update")}
                    </Button>
                    <DialogTrigger disableButtonEnhancement>
                        <Button
                            id={"email-management-cancel-btn"}
                            appearance="secondary"
                            shape="circular"
                            disabled={processing}
                            onClick={resetDialog}>
                            Cancel
                        </Button>
                    </DialogTrigger>
                </DialogActions>
            </DialogSurface>
        </Dialog>
    );
}

export default EmailManagementDialog;