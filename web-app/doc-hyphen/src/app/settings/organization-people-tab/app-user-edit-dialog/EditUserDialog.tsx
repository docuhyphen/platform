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
import {AppUserDetailedDto} from "../../../models/models.tsx";
import {updateOrganizationUser} from "../../../../services/organizationApi.ts";

interface EditUserDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    organizationId: string;
    user: AppUserDetailedDto | null;
    onComplete: () => void;
}

const EditUserDialog: React.FC<EditUserDialogProps> = ({
                                                           isOpen,
                                                           onDismiss,
                                                           organizationId,
                                                           user,
                                                           onComplete
                                                       }) =>
{
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

    const handleSave = async () =>
    {
        if (!organizationId || !user?.id) return;

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
                        firstName,
                        lastName
                    }
                },
                token || undefined
            );

            onComplete();
        }
        catch (err: any)
        {
            setError(err.message || "Failed to update user");
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

    return (
        <Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Edit User</DialogTitle>
                    <DialogContent>
                        {error && <div style={{color: 'red', marginBottom: '10px'}}>{error}</div>}

                        <Field label="First Name" required>
                            <Input
                                type="text"
                                value={firstName}
                                onChange={(e) => setFirstName(e.target.value)}
                            />
                        </Field>

                        <Field label="Last Name" required>
                            <Input
                                type="text"
                                value={lastName}
                                onChange={(e) => setLastName(e.target.value)}
                            />
                        </Field>

                        <Field label="Role" required>
                            <Dropdown
                                value={role}
                                onOptionSelect={(_, data) => data.optionValue && setRole(data.optionValue)}
                            >
                                <Option value="ORG_ADMIN">Organization Admin</Option>
                                <Option value="ORG_MEMBER">Organization Member</Option>
                            </Dropdown>
                        </Field>

                        <Field label="Status">
                            <Switch
                                checked={isActive}
                                onChange={(_, data) => setIsActive(data.checked)}
                                label="Active"
                            />
                        </Field>
                    </DialogContent>
                </DialogBody>
                <DialogActions>
                    <Button
                        appearance="primary"
                        shape="circular"
                        disabled={savingData || !isFormValid || !hasChanges}
                        onClick={handleSave}
                    >
                        {savingData && <Spinner size="tiny"/>}
                        Update User
                    </Button>
                    <DialogTrigger disableButtonEnhancement>
                        <Button
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