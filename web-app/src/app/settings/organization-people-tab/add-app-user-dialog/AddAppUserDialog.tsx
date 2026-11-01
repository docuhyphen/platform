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
import React from "react";
import {useAddAppUserDialogStyles} from "./AddAppUserDialogStyles.tsx";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {useAddAppUserForm} from "./useAddAppUserForm.ts";
import AppUserNameFields from "../app-user-form-fields/AppUserNameFields.tsx";
import AppUserRolesField from "../app-user-form-fields/AppUserRolesField.tsx";

interface AddUserDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    organizationId: string;
    onComplete: () => void;
}

const nameFieldIds = {
    firstNameField: "add-app-user-dialog-field-2",
    firstNameInput: "add-user-first-name-input",
    lastNameField: "add-app-user-dialog-field-3",
    lastNameInput: "add-user-last-name-input",
};

const rolesFieldIds = {
    field: "add-app-user-dialog-field-4",
    dropdown: "add-user-role-dropdown",
    optionPrefix: "add-app-user-dialog-option-1",
};

const AddAppUserDialog: React.FC<AddUserDialogProps> = (
    {
        isOpen,
        onDismiss,
        organizationId,
        onComplete
    }) =>
{
    const styles = useAddAppUserDialogStyles();
    const globalStyles = useGlobalStyles();
    const form = useAddAppUserForm({isOpen, organizationId, onDismiss, onComplete});

    return (
        <Dialog modalType="alert"
                open={isOpen}>
            <DialogSurface id={"add-app-user-dialog-dialog-surface-1"}>
                <DialogBody id={"add-app-user-dialog-dialog-body-1"}>
                    <DialogTitle id={"add-app-user-dialog-dialog-title-1"}>Add New User</DialogTitle>
                    <DialogContent id={"add-app-user-dialog-dialog-content-1"}
                                   className={styles.dialogContentContainer}>
                        {form.error && <div id={"add-app-user-dialog-div-1"}
                                            className={styles.errorContainer}>{form.error}</div>}

                        <Field id={"add-app-user-dialog-field-1"}
                               label="Email"
                               required>
                            <Input
                                id={"add-user-email-input"}
                                type="email"
                                value={form.email}
                                maxLength={254}
                                onChange={(e) => form.setEmail(e.target.value)}
                                onKeyDown={(e) => { if (e.key === "Enter") form.handleSave(); }}
                            />
                        </Field>

                        <AppUserNameFields ids={nameFieldIds}
                                           firstName={form.firstName}
                                           lastName={form.lastName}
                                           onFirstNameChange={form.setFirstName}
                                           onLastNameChange={form.setLastName}
                                           onEnter={form.handleSave}/>

                        <AppUserRolesField ids={rolesFieldIds}
                                           roles={form.roles}
                                           onRolesChange={form.setRoles}/>
                    </DialogContent>
                </DialogBody>
                <DialogActions id={"add-app-user-dialog-dialog-actions-1"}>
                    <Button
                        id={"add-user-save-btn"}
                        appearance="primary"
                        shape="circular"
                        className={globalStyles.buttonWithLoading}
                        disabled={form.savingData || !form.isFormValid}
                        onClick={form.handleSave}
                    >
                        {form.savingData && <Spinner id={"add-app-user-dialog-spinner-1"}
                                                     size="tiny"/>}
                        {form.savingData ? "Adding…" : "Add User"}
                    </Button>
                    <DialogTrigger disableButtonEnhancement>
                        <Button
                            id={"add-user-cancel-btn"}
                            appearance="secondary"
                            shape="circular"
                            disabled={form.savingData}
                            onClick={form.onClose}
                        >
                            Cancel
                        </Button>
                    </DialogTrigger>
                </DialogActions>
            </DialogSurface>
        </Dialog>
    );
};

export default AddAppUserDialog;
