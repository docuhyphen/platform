import {useEffect, useState} from "react";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {addOrganizationUser} from "../../../../services/organizationApi.ts";
import {OrganizationRoleName} from '../../../../services/types/roles.ts';
import {isValidEmail} from "../../../../utils/helpers.ts";
import {extractAddUserErrorMessage} from "./addAppUserErrorMessages.ts";

interface AddAppUserFormOptions
{
    isOpen: boolean;
    organizationId: string;
    onDismiss: () => void;
    onComplete: () => void;
}

export const useAddAppUserForm = ({isOpen, organizationId, onDismiss, onComplete}: AddAppUserFormOptions) =>
{
    const {token} = useAuth();
    const [email, setEmail] = useState("");
    const [firstName, setFirstName] = useState("");
    const [lastName, setLastName] = useState("");
    const [roles, setRoles] = useState<OrganizationRoleName[]>([OrganizationRoleName.ORG_MEMBER]);
    const [savingData, setSavingData] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const resetForm = () =>
    {
        setEmail("");
        setFirstName("");
        setLastName("");
        setRoles([OrganizationRoleName.ORG_MEMBER]);
        setError(null);
    };

    useEffect(() =>
    {
        if (!isOpen)
        {
            resetForm();
        }
    }, [isOpen]);

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
                    roles,
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
        catch (err: unknown)
        {
            setError(extractAddUserErrorMessage(err));
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

    const isFormValid = email.trim() && firstName.trim() && lastName.trim() && roles.length > 0;

    return {
        email,
        setEmail,
        firstName,
        setFirstName,
        lastName,
        setLastName,
        roles,
        setRoles,
        savingData,
        error,
        isFormValid,
        handleSave,
        onClose,
    };
};
