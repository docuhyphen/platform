import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Dropdown,
    Field,
    Input,
    Option,
    Spinner,
    Switch
} from "@fluentui/react-components";
import React, {useEffect, useState} from "react";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {AppUserDetailedDto, AppUserRole, AppUserRoleDisplayNames} from "../../../models/models.tsx";
import {updateOrganizationUser} from "../../../../services/organizationApi.ts";
import {useEditUserDialogStyles} from "./EditUserDialogStyles.tsx";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";

interface EditUserDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    organizationId: string;
    user: AppUserDetailedDto | null;
    onComplete: () => void;
}

const EditUserDialog: React.FC<EditUserDialogProps> = (
    {
        isOpen,
        onDismiss,
        organizationId,
        user,
        onComplete
    }) =>
{
    const styles = useEditUserDialogStyles()
    const globStyles = useGlobalStyles()

    const {token} = useAuth();
    const [firstName, setFirstName] = useState("");
    const [lastName, setLastName] = useState("");
    const [role, setRole] = useState("");
    const [isActive, setIsActive] = useState(true);
    const [savingData, setSavingData] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() =>
    {
        if (user)
        {
            setFirstName(user.person?.firstName || "");
            setLastName(user.person?.lastName || "");
            setRole(user.role.toString());
            setIsActive(user.isActive);
        }
    }, [user]);

    const extractErrorMessage = (e: any): string =>
    {
        if (typeof e === "string") return e;
        const msg = e?.errorMessage || e?.message || e?.response?.data?.errorMessage || e?.response?.data?.message;
        if (msg) return msg;
        return "Something went wrong. Please try again in a moment.";
    }

    const handleSave = async () =>
    {
        if (!organizationId || !user?.id) return;

        if (!firstName.trim() || !lastName.trim())
        {
            setError("First name and last name are required.");
            return;
        }

        setSavingData(true);
        setError(null);

        try
        {
            await updateOrganizationUser(
                organizationId,
                user.id.toString(),
                {
                    role,
                    isActive,
                    person: {
                        firstName: firstName.trim(),
                        lastName: lastName.trim()
                    }
                },
                token || undefined
            );

            onComplete();
        }
        catch (err: any)
        {
            setError(extractErrorMessage(err));
            console.error("Failed to update user:", err);
        }
        finally
        {
            setSavingData(false);
        }
    };

    const onClose = () =>
    {
        setError(null);
        onDismiss();
    };

    const hasChanges =
        firstName !== user?.person?.firstName ||
        lastName !== user?.person?.lastName ||
        role !== user?.role.toString() ||
        isActive !== user?.isActive;

    const isFormValid = firstName && lastName && role;

    const onSelectRole = (_, data) => {

        data.optionValue && setRole(data.optionValue)
    };

    return (
        <Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Edit User</DialogTitle>
                    <DialogContent className={styles.dialogContentContainer}>
                        { error && <div className={styles.errorContainer}>{error}</div>}

                        <Field label="First Name" required>
                            <Input
                                id={"edit-user-first-name-input"}
                                type="text"
                                value={firstName}
                                maxLength={50}
                                onChange={(e) => setFirstName(e.target.value)}
                                onKeyDown={(e) => { if (e.key === "Enter") handleSave(); }}
                            />
                        </Field>

                        <Field label="Last Name" required>
                            <Input
                                id={"edit-user-last-name-input"}
                                type="text"
                                value={lastName}
                                maxLength={50}
                                onChange={(e) => setLastName(e.target.value)}
                                onKeyDown={(e) => { if (e.key === "Enter") handleSave(); }}
                            />
                        </Field>

                        <Field label="Role" required>
                            <Dropdown
                                id={"edit-user-role-dropdown"}
                                selectedOptions={[role]}
                                placeholder={AppUserRoleDisplayNames[role as keyof typeof AppUserRoleDisplayNames]}
                                onOptionSelect={onSelectRole}
                            >
                                <Option value="ORG_ADMIN">Organization Admin</Option>
                                <Option value="ORG_MEMBER">Organization Member</Option>
                            </Dropdown>
                        </Field>

                        <Field>
                            <Switch
                                id={"edit-user-active-switch"}
                                disabled={user?.role == AppUserRole.ORG_ADMIN}
                                checked={isActive}
                                onChange={(_, data) => setIsActive(data.checked)}
                                label={isActive ? "Deactivate" : "Activate"}
                            />
                        </Field>
                    </DialogContent>
                </DialogBody>
                <DialogActions>
                    <Button
                        id={"edit-user-save-btn"}
                        appearance="primary"
                        shape="circular"
                        disabled={savingData || !isFormValid || !hasChanges}
                        onClick={handleSave}
                        className={globStyles.buttonWithLoading}
                    >
                        {savingData && <Spinner size="tiny"/>}
                        {savingData ? "Updating…" : "Update User"}
                    </Button>
                    <DialogTrigger disableButtonEnhancement>
                        <Button
                            id={"edit-user-cancel-btn"}
                            appearance="secondary"
                            shape="circular"
                            disabled={savingData}
                            onClick={onClose}
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