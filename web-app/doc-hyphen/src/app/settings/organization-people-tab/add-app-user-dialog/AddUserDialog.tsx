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
import React, {useState} from "react";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {addOrganizationUser} from "../../../../services/organizationApi.ts";
import {useAddAppUserDialogStyles} from "./AddAppUserDialogStyles.tsx";

interface AddUserDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    organizationId: string;
    onComplete: () => void;
}

const AddUserDialog: React.FC<AddUserDialogProps> = (
    {
        isOpen,
        onDismiss,
        organizationId,
        onComplete
    }) =>
{
    const styles = useAddAppUserDialogStyles()
    const {token} = useAuth();
    const [email, setEmail] = useState("");
    const [firstName, setFirstName] = useState("");
    const [lastName, setLastName] = useState("");
    const [role, setRole] = useState("ORG_MEMBER");
    const [savingData, setSavingData] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleSave = async () =>
    {
        console.log("handle save", organizationId)

        if (!organizationId) return;

        setSavingData(true);
        setError(null);

        try
        {
            await addOrganizationUser(
                organizationId,
                {
                    email,
                    role,
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
            setError(err.message || "Failed to add user");
            console.error("Failed to add user:", err);
        }
        finally
        {
            setSavingData(false);
        }
    };

    const onClose = () =>
    {
        setEmail("");
        setFirstName("");
        setLastName("");
        setRole("ORG_MEMBER");
        setError(null);
        onDismiss();
    };

    const isFormValid = email && firstName && lastName && role;

    return (
        <Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Add New User</DialogTitle>
                    <DialogContent className={styles.dialogContentContainer}>
                        {error && <div style={{color: 'red', marginBottom: '10px'}}>{error}</div>}

                        <Field label="Email" required>
                            <Input
                                type="email"
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                            />
                        </Field>

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
                    </DialogContent>
                </DialogBody>
                <DialogActions>
                    <Button
                        appearance="primary"
                        shape="circular"
                        disabled={savingData || !isFormValid}
                        onClick={handleSave}
                    >
                        {savingData && <Spinner size="tiny"/>}
                        Add User
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

export default AddUserDialog;