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
    Spinner
} from "@fluentui/react-components";
import React, {useEffect, useState} from "react";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {addOrganizationUser} from "../../../../services/organizationApi.ts";
import {useAddAppUserDialogStyles} from "./AddAppUserDialogStyles.tsx";
import {AppUserRoleDisplayNames} from "../../../models/models.tsx";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {isValidEmail} from "../../../../utils/helpers.ts";

interface AddUserDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    organizationId: string;
    onComplete: () => void;
}

const mapServerErrorMessage = (raw: string | undefined | null): string =>
{
    if (!raw) return "Something went wrong. Please try again in a moment.";
    const lower = raw.toLowerCase();
    if (lower.includes("already exists"))
    {
        return "A user with that email is already in this organization.";
    }
    if (lower.includes("valid email"))
    {
        return "Please enter a valid email address.";
    }
    if (lower.includes("permission"))
    {
        return "You do not have permission to add users.";
    }
    if (lower.includes("capacity") || lower.includes("limit"))
    {
        if (lower.includes("organization user limit reached") || lower.includes("limit is"))
        {
            return raw;
        }
        return "Your organization has reached its member limit. Upgrade your plan to add more users.";
    }
    return raw;
};

const AddAppUserDialog: React.FC<AddUserDialogProps> = (
    {
        isOpen,
        onDismiss,
        organizationId,
        onComplete
    }) =>
{
    const styles = useAddAppUserDialogStyles()
    const globalStyles = useGlobalStyles()
    const {token} = useAuth();
    const [email, setEmail] = useState("");
    const [firstName, setFirstName] = useState("");
    const [lastName, setLastName] = useState("");
    const [role, setRole] = useState("ORG_MEMBER");
    const [savingData, setSavingData] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const resetForm = () =>
    {
        setEmail("");
        setFirstName("");
        setLastName("");
        setRole("ORG_MEMBER");
        setError(null);
    };

    useEffect(() =>
    {
        if (!isOpen)
        {
            resetForm();
        }
    }, [isOpen]);

    const extractErrorMessage = (e: any): string =>
    {
        if (typeof e === "string") return mapServerErrorMessage(e);
        const msg = e?.errorMessage || e?.message || e?.response?.data?.errorMessage || e?.response?.data?.message;
        return mapServerErrorMessage(msg);
    }

    const handleSave = async () =>
    {
        if (!organizationId) return;

        if (!isValidEmail(email.trim()))
        {
            setError("Please enter a valid email address.");
            return;
        }

        if (!firstName.trim() || !lastName.trim())
        {
            setError("First name and last name are required.");
            return;
        }

        setSavingData(true);
        setError(null);

        try
        {
            await addOrganizationUser(
                organizationId,
                {
                    email: email.trim().toLowerCase(),
                    role,
                    person: {
                        firstName: firstName.trim(),
                        lastName: lastName.trim()
                    }
                },
                token || undefined
            );

            resetForm();
            onComplete();
        }
        catch (err: any)
        {
            setError(extractErrorMessage(err));
            console.error("Failed to add user:", err);
        }
        finally
        {
            setSavingData(false);
        }
    };

    const onClose = () =>
    {
        resetForm();
        onDismiss();
    };

    const isFormValid = email.trim() && firstName.trim() && lastName.trim() && role;

    return (
        <Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Add New User</DialogTitle>
                    <DialogContent className={styles.dialogContentContainer}>
                        { error && <div className={styles.errorContainer}>{error}</div>}

                        <Field label="Email" required>
                            <Input
                                id={"add-user-email-input"}
                                type="email"
                                value={email}
                                maxLength={254}
                                onChange={(e) => setEmail(e.target.value)}
                                onKeyDown={(e) => { if (e.key === "Enter") handleSave(); }}
                            />
                        </Field>

                        <Field label="First Name" required>
                            <Input
                                id={"add-user-first-name-input"}
                                type="text"
                                value={firstName}
                                maxLength={50}
                                onChange={(e) => setFirstName(e.target.value)}
                                onKeyDown={(e) => { if (e.key === "Enter") handleSave(); }}
                            />
                        </Field>

                        <Field label="Last Name" required>
                            <Input
                                id={"add-user-last-name-input"}
                                type="text"
                                value={lastName}
                                maxLength={50}
                                onChange={(e) => setLastName(e.target.value)}
                                onKeyDown={(e) => { if (e.key === "Enter") handleSave(); }}
                            />
                        </Field>

                        <Field label="Role" required>
                            <Dropdown
                                id={"add-user-role-dropdown"}
                                selectedOptions={[role]}
                                placeholder={AppUserRoleDisplayNames[role as keyof typeof AppUserRoleDisplayNames]}
                                onOptionSelect={(_, data) => data.optionValue && setRole(data.optionValue)}
                            >
                                <Option value="ORG_ADMIN">Organization Admin</Option>
                                <Option value="ORG_MEMBER">Organization Member</Option>
                            </Dropdown>
                        </Field>
                    </DialogContent>
                </DialogBody>
                <DialogActions>
                    <Button
                        id={"add-user-save-btn"}
                        appearance="primary"
                        shape="circular"
                        className={globalStyles.buttonWithLoading}
                        disabled={savingData || !isFormValid}
                        onClick={handleSave}
                    >
                        {savingData && <Spinner size="tiny"/>}
                        {savingData ? "Adding…" : "Add User"}
                    </Button>
                    <DialogTrigger disableButtonEnhancement>
                        <Button
                            id={"add-user-cancel-btn"}
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

export default AddAppUserDialog;
