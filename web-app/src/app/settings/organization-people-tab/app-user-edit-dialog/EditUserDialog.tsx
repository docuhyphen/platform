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
    Spinner,
    Switch
} from "@fluentui/react-components";
import React from "react";
import {AppUserPublicDto} from "../../../models/models.tsx";
import {useEditUserDialogStyles} from "./EditUserDialogStyles.tsx";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {useEditUserForm} from "./useEditUserForm.ts";
import AppUserNameFields from "../app-user-form-fields/AppUserNameFields.tsx";
import AppUserRolesField from "../app-user-form-fields/AppUserRolesField.tsx";

interface EditUserDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    organizationId: string;
    user: AppUserPublicDto | null;
    onComplete: () => void;
}

const nameFieldIds = {
    firstNameField: "edit-user-dialog-field-1",
    firstNameInput: "edit-user-first-name-input",
    lastNameField: "edit-user-dialog-field-2",
    lastNameInput: "edit-user-last-name-input",
};

const rolesFieldIds = {
    field: "edit-user-dialog-field-3",
    dropdown: "edit-user-role-dropdown",
    optionPrefix: "edit-user-dialog-option-1",
};

const EditUserDialog: React.FC<EditUserDialogProps> = (
    {
        isOpen,
        onDismiss,
        organizationId,
        user,
        onComplete
    }) =>
{
    const styles = useEditUserDialogStyles();
    const globStyles = useGlobalStyles();
    const form = useEditUserForm({organizationId, user, onDismiss, onComplete});

    return (
        <Dialog modalType="alert"
                open={isOpen}>
            <DialogSurface id={"edit-user-dialog-dialog-surface-1"}>
                <DialogBody id={"edit-user-dialog-dialog-body-1"}>
                    <DialogTitle id={"edit-user-dialog-dialog-title-1"}>Edit User</DialogTitle>
                    <DialogContent id={"edit-user-dialog-dialog-content-1"}
                                   className={styles.dialogContentContainer}>
                        {form.error && <div id={"edit-user-dialog-div-1"}
                                            className={styles.errorContainer}>{form.error}</div>}

                        <AppUserNameFields ids={nameFieldIds}
                                           firstName={form.firstName}
                                           lastName={form.lastName}
                                           onFirstNameChange={form.setFirstName}
                                           onLastNameChange={form.setLastName}
                                           onEnter={form.handleSave}/>

                        <AppUserRolesField ids={rolesFieldIds}
                                           roles={form.roles}
                                           onRolesChange={form.setRoles}/>

                        <Field id={"edit-user-dialog-field-4"}>
                            <Switch
                                id={"edit-user-active-switch"}
                                disabled={form.activeSwitchLocked}
                                checked={form.isActive}
                                onChange={(_, data) => form.setIsActive(data.checked)}
                                label={form.isActive ? "Deactivate" : "Activate"}
                            />
                        </Field>
                    </DialogContent>
                </DialogBody>
                <DialogActions id={"edit-user-dialog-dialog-actions-1"}>
                    <Button
                        id={"edit-user-save-btn"}
                        appearance="primary"
                        shape="circular"
                        disabled={form.savingData || !form.isFormValid || !form.hasChanges}
                        onClick={form.handleSave}
                        className={globStyles.buttonWithLoading}
                    >
                        {form.savingData && <Spinner id={"edit-user-dialog-spinner-1"}
                                                     size="tiny"/>}
                        {form.savingData ? "Updating…" : "Update User"}
                    </Button>
                    <DialogTrigger disableButtonEnhancement>
                        <Button
                            id={"edit-user-cancel-btn"}
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

export default EditUserDialog;
