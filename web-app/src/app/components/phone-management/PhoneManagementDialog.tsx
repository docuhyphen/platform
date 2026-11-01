import React from "react";
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
import {usePhoneManagementDialogStyles} from "./PhoneManagementDialogStyles.tsx";
import {useGlobalStyles} from "../../../GlobalStyles.tsx";
import {PhoneManagementMode, usePhoneManagement} from "./usePhoneManagement.ts";

export {PhoneManagementMode} from "./usePhoneManagement.ts";

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
    const styles = usePhoneManagementDialogStyles();
    const globalStyles = useGlobalStyles();
    const {
        phoneNumber,
        processing,
        addOrEditInitiated,
        verificationCode,
        error,
        onPhoneNumberChange,
        onVerificationCodeChange,
        onAddOrUpdatePhoneNumber,
        resetDialog,
    } = usePhoneManagement({contactDetails, mode, onDismiss, onComplete});

    return (
        <Dialog modalType="alert"
                open={isOpen}>
            <DialogSurface id={"phone-management-dialog-dialog-surface-1"}>
                <DialogBody id={"phone-management-dialog-dialog-body-1"}>
                    <DialogTitle id={"phone-management-dialog-dialog-title-1"}>
                        {mode === PhoneManagementMode.ADD ? "Add new phone number" : "Edit phone number"}
                    </DialogTitle>
                    <DialogContent id={"phone-management-dialog-dialog-content-1"}
                                   className={styles.dialogContentContainer}>
                        {error && <div id={"phone-management-dialog-div-1"}
                                       className={styles.errorContainer}>{error}</div>}

                        <Field id={"phone-management-dialog-field-1"}
                               label="Phone number">
                            <Input
                                id={"phone-management-phone-input"}
                                type="text"
                                maxLength={16}
                                value={phoneNumber || ''}
                                onChange={onPhoneNumberChange}
                                onKeyDown={(e) => { if (e.key === "Enter") onAddOrUpdatePhoneNumber(); }}
                                disabled={addOrEditInitiated || processing}
                            />
                        </Field>

                        {addOrEditInitiated && (
                            <Field id={"phone-management-dialog-field-2"}
                                   label="Verification code">
                                <Input
                                    id={"phone-management-verification-input"}
                                    type="text"
                                    value={verificationCode}
                                    maxLength={8}
                                    onChange={onVerificationCodeChange}
                                    onKeyDown={(e) => { if (e.key === "Enter") onAddOrUpdatePhoneNumber(); }}
                                />
                            </Field>
                        )}
                    </DialogContent>
                </DialogBody>
                <DialogActions id={"phone-management-dialog-dialog-actions-1"}>
                    <Button
                        id={"phone-management-submit-btn"}
                        appearance="primary"
                        shape="circular"
                        className={globalStyles.buttonWithLoading}
                        disabled={processing}
                        onClick={onAddOrUpdatePhoneNumber}
                    >
                        {processing && <Spinner id={"phone-management-dialog-spinner-1"}
                                                size="tiny"/>}
                        {addOrEditInitiated ? "Verify" : (mode === PhoneManagementMode.ADD ? "Add" : "Update")}
                    </Button>
                    <DialogTrigger disableButtonEnhancement>
                        <Button
                            id={"phone-management-cancel-btn"}
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
};

export default PhoneManagementDialog;
