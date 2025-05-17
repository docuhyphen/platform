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
    completePhoneAddition,
    completePhoneUpdate,
    initiatePhoneAddition,
    initiatePhoneUpdate
} from "../../../services/contactDetailsApi";
import {usePhoneManagementDialogStyles} from "./PhoneManagementDialogStyles.tsx";

export enum PhoneManagementMode
{
    ADD,
    EDIT
}

interface PhoneManagementDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    contactDetails?: ContactDetailsDetailedDto;
    onComplete?: (contactDetails: ContactDetailsDetailedDto) => void;
    mode?: PhoneManagementMode;
}

const PhoneManagementDialog: React.FC<PhoneManagementDialogProps> = (
    {
        contactDetails,
        mode,
        onDismiss,
        isOpen,
        onComplete
    }) =>
{
    const styles = usePhoneManagementDialogStyles()
    const {token} = useAuth();
    const [phoneNumber, setPhoneNumber] = useState<string | undefined>(contactDetails?.phoneNumber);
    const [processing, setProcessing] = useState(false);
    const [addOrEditInitiated, setAddOrEditInitiated] = useState(false);
    const [verificationCode, setVerificationCode] = useState('');
    const [error, setError] = useState<string | null>(null);

    const onPhoneNumberChange = (e: React.ChangeEvent<HTMLInputElement>) =>
    {
        setPhoneNumber(e.target.value);
    }

    const onVerificationCodeChange = (e: React.ChangeEvent<HTMLInputElement>) =>
    {
        setVerificationCode(e.target.value);
    }

    const onInitiateAddPhoneNumber = async () =>
    {
        console.log(processing, contactDetails)
        if (processing || !contactDetails?.id) return;

        alert("")
        if (!phoneNumber)
        {
            setError("Phone number is required");
            return;
        }

        setProcessing(true);
        setError(null);

        try
        {
            await initiatePhoneAddition(contactDetails.id, phoneNumber, token);
            setAddOrEditInitiated(true);
        }
        catch (e: any)
        {
            setError(e.message || "Failed to initiate phone number addition");
            console.error("Failed to initiate phone number addition:", e);
        }
        finally
        {
            setProcessing(false);
        }
    }

    const onCompleteAddPhoneNumber = async () =>
    {
        if (processing || !contactDetails?.id) return;

        if (!phoneNumber)
        {
            setError("Phone number is required");
            return;
        }

        if (!verificationCode)
        {
            setError("Verification code is required");
            return;
        }

        setProcessing(true);
        setError(null);

        try
        {
            await completePhoneAddition(contactDetails.id, phoneNumber, verificationCode, token);

            const updatedContactDetails = {
                ...contactDetails,
                phoneNumber: phoneNumber
            } as ContactDetailsDetailedDto;

            if (onComplete)
            {
                onComplete(updatedContactDetails);
                resetDialog()
            }

            onDismiss();
        }
        catch (e: any)
        {
            setError(e.message || "Failed to complete phone number addition");
            console.error("Failed to complete phone number addition:", e);
        }
        finally
        {
            setProcessing(false);
        }
    }

    const onInitiateEditPhoneNumber = async () =>
    {
        if (processing || !contactDetails?.id) return;

        if (!phoneNumber)
        {
            setError("Phone number is required");
            return;
        }

        setProcessing(true);
        setError(null);

        try
        {
            await initiatePhoneUpdate(contactDetails.id, phoneNumber, token);
            setAddOrEditInitiated(true);
        }
        catch (e: any)
        {
            setError(e.message || "Failed to initiate phone number update");
            console.error("Failed to initiate phone number update:", e);
        }
        finally
        {
            setProcessing(false);
        }
    }

    const onCompleteEditPhoneNumber = async () =>
    {
        if (processing || !contactDetails?.id) return;

        if (!phoneNumber)
        {
            setError("Phone number is required");
            return;
        }

        if (!verificationCode)
        {
            setError("Verification code is required");
            return;
        }

        setProcessing(true);
        setError(null);

        try
        {
            await completePhoneUpdate(contactDetails.id, phoneNumber, verificationCode, token);

            const updatedContactDetails = {
                ...contactDetails,
                phoneNumber: phoneNumber
            } as ContactDetailsDetailedDto;

            if (onComplete)
            {
                onComplete(updatedContactDetails);
                resetDialog()
            }

            onDismiss();
        }
        catch (e: any)
        {
            setError(e.message || "Failed to complete phone number update");
            console.error("Failed to complete phone number update:", e);
        }
        finally
        {
            setProcessing(false);
        }
    }

    const onAddOrUpdatePhoneNumber = () =>
    {
        if (addOrEditInitiated)
        {
            if (mode === PhoneManagementMode.ADD)
            {
                onCompleteAddPhoneNumber();
            }
            else
            {
                onCompleteEditPhoneNumber();
            }
        }
        else
        {
            if (mode === PhoneManagementMode.ADD)
            {
                onInitiateAddPhoneNumber();
            }
            else
            {
                onInitiateEditPhoneNumber();
            }
        }
    }

    const resetDialog = () =>
    {
        setPhoneNumber(contactDetails?.phoneNumber);
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
                        {mode === PhoneManagementMode.ADD ? "Add new phone number" : "Edit phone number"}
                    </DialogTitle>
                    <DialogContent className={styles.dialogContentContainer}>
                        {error && <div style={{color: 'red', marginBottom: '10px'}}>{error}</div>}

                        <Field label="Phone number">
                            <Input
                                type="text"
                                maxLength={16}
                                value={phoneNumber || ''}
                                onChange={onPhoneNumberChange}
                                disabled={addOrEditInitiated || processing}
                            />
                        </Field>

                        {addOrEditInitiated && (
                            <Field label="Verification code">
                                <Input
                                    type="text"
                                    value={verificationCode}
                                    maxLength={8}
                                    onChange={onVerificationCodeChange}
                                />
                            </Field>
                        )}
                    </DialogContent>
                </DialogBody>
                <DialogActions>
                    <Button
                        appearance="primary"
                        shape="circular"
                        disabled={processing}
                        onClick={onAddOrUpdatePhoneNumber}
                    >
                        {processing && <Spinner size="tiny"/>}
                        {addOrEditInitiated ? "Verify" : (mode === PhoneManagementMode.ADD ? "Add" : "Update")}
                    </Button>
                    <DialogTrigger disableButtonEnhancement>
                        <Button
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

export default PhoneManagementDialog;