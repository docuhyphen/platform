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
import {useEmailManagementDialogStyles} from "./EmailManagementDialogStyles.tsx";
import {useGlobalStyles} from "../../../GlobalStyles.tsx";
import {EmailManagementMode, useEmailManagement} from "./useEmailManagement.ts";

export {EmailManagementMode} from "./useEmailManagement.ts";

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
    const styles = useEmailManagementDialogStyles();
    const globalStyles = useGlobalStyles();
    const {
        email,
        processing,
        addOrEditInitiated,
        verificationCode,
        error,
        onEmailChange,
        onVerificationCodeChange,
        onAddOrUpdateEmail,
        resetDialog,
    } = useEmailManagement({contactDetails, mode, onDismiss, onComplete});

    return (
        <Dialog modalType="alert"
                open={isOpen}>
            <DialogSurface id={"email-management-dialog-dialog-surface-1"}>
                <DialogBody id={"email-management-dialog-dialog-body-1"}>
                    <DialogTitle id={"email-management-dialog-dialog-title-1"}>
                        {mode === EmailManagementMode.ADD ? "Add new email" : "Edit email"}
                    </DialogTitle>
                    <DialogContent id={"email-management-dialog-dialog-content-1"}
                                   className={styles.dialogContentContainer}>
                        {error && <div id={"email-management-dialog-div-1"}
                                       className={styles.errorContainer}>{error}</div>}

                        <Field id={"email-management-dialog-field-1"}
                               label="Email">
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
                            <Field id={"email-management-dialog-field-2"}
                                   label="Verification code">
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
                <DialogActions id={"email-management-dialog-dialog-actions-1"}>
                    <Button
                        id={"email-management-submit-btn"}
                        appearance="primary"
                        shape="circular"
                        className={globalStyles.buttonWithLoading}
                        disabled={processing}
                        onClick={onAddOrUpdateEmail}
                    >
                        {processing && <Spinner id={"email-management-dialog-spinner-1"}
                                                size="tiny"/>}
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
};

export default EmailManagementDialog;
