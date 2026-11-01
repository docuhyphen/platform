import React from "react";
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
    Spinner,
    Text
} from "@fluentui/react-components";
import {useAppUserEmailUpdateDialogStyles} from "./AppUserEmailUpdateDialogStyles.tsx";
import {useGlobalStyles} from "../../../GlobalStyles.tsx";
import {useAppUserEmailUpdate} from "./useAppUserEmailUpdate.ts";
import {AppUserEmailUpdateStage, appUserEmailUpdatePrimaryLabel} from "./appUserEmailUpdateStage.ts";
import AppUserEmailUpdateCodeField from "./AppUserEmailUpdateCodeField.tsx";

interface AppUserEmailUpdateDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    currentEmail?: string;
}

const AppUserEmailUpdateDialog: React.FC<AppUserEmailUpdateDialogProps> = (
    {
        isOpen,
        onDismiss,
        currentEmail
    }) =>
{
    const styles = useAppUserEmailUpdateDialogStyles();
    const globalStyles = useGlobalStyles();
    const {
        email,
        setEmail,
        processing,
        stage,
        oldEmailCode,
        setOldEmailCode,
        newEmailCode,
        setNewEmailCode,
        error,
        onPrimaryClick,
        resetDialog,
    } = useAppUserEmailUpdate({currentEmail, onDismiss});

    return (
        <Dialog modalType="alert"
                open={isOpen}>
            <DialogSurface id={"app-user-email-update-dialog-dialog-surface-1"}>
                <DialogBody id={"app-user-email-update-dialog-dialog-body-1"}>
                    <DialogTitle id={"app-user-email-update-dialog-dialog-title-1"}>
                        Update Email Address
                    </DialogTitle>
                    <DialogContent id={"app-user-email-update-dialog-dialog-content-1"}
                                   className={styles.dialogContentContainer}>
                        {error && <div id={"app-user-email-update-dialog-div-1"}
                                       className={styles.errorContainer}>{error}</div>}

                        {stage === AppUserEmailUpdateStage.ENTER_EMAIL && (
                            <Text id={"app-user-email-update-dialog-text-1"}>
                                Updating your email is a two-step verification process: we'll send a code to your
                                current email first, then to your new email. You'll be signed out from all
                                devices on completion.
                            </Text>
                        )}

                        <Field id={"app-user-email-update-dialog-field-1"}
                               label="New Email">
                            <Input
                                id={"app-user-email-update-new-email-input"}
                                type="email"
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                                onKeyDown={(e) => { if (e.key === "Enter") onPrimaryClick(); }}
                                maxLength={254}
                                disabled={stage !== AppUserEmailUpdateStage.ENTER_EMAIL || processing}
                            />
                        </Field>

                        {stage === AppUserEmailUpdateStage.CONFIRM_OLD && (
                            <AppUserEmailUpdateCodeField fieldId={"app-user-email-update-dialog-field-2"}
                                                         textId={"app-user-email-update-dialog-text-2"}
                                                         inputId={"app-user-email-update-old-code-input"}
                                                         label="Code sent to your current email"
                                                         sentTo={currentEmail}
                                                         value={oldEmailCode}
                                                         onChange={setOldEmailCode}
                                                         onSubmit={onPrimaryClick}/>
                        )}

                        {stage === AppUserEmailUpdateStage.CONFIRM_NEW && (
                            <AppUserEmailUpdateCodeField fieldId={"app-user-email-update-dialog-field-3"}
                                                         textId={"app-user-email-update-dialog-text-3"}
                                                         inputId={"app-user-email-update-new-code-input"}
                                                         label="Code sent to your new email"
                                                         sentTo={email}
                                                         value={newEmailCode}
                                                         onChange={setNewEmailCode}
                                                         onSubmit={onPrimaryClick}/>
                        )}
                    </DialogContent>
                </DialogBody>
                <DialogActions id={"app-user-email-update-dialog-dialog-actions-1"}>
                    <Button
                        id={"app-user-email-update-submit-btn"}
                        appearance="primary"
                        shape="circular"
                        className={globalStyles.buttonWithLoading}
                        disabled={processing}
                        onClick={onPrimaryClick}
                    >
                        {processing && <Spinner id={"app-user-email-update-dialog-spinner-1"}
                                                size="tiny"/>}
                        {appUserEmailUpdatePrimaryLabel(stage)}
                    </Button>
                    <DialogTrigger disableButtonEnhancement>
                        <Button
                            id={"app-user-email-update-cancel-btn"}
                            appearance="secondary"
                            shape="circular"
                            disabled={processing}
                            onClick={resetDialog}
                        >
                            Cancel
                        </Button>
                    </DialogTrigger>
                </DialogActions>
            </DialogSurface>
        </Dialog>
    );
};

export default AppUserEmailUpdateDialog;
