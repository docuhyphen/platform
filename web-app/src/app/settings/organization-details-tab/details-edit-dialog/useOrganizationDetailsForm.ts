import {useState} from "react";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {OrganizationDetailedDto} from "../../../models/models.tsx";
import {updateOrganization} from "../../../../services/organizationApi.ts";
import {extractDialogErrorMessage} from "../../components/dialogErrorMessage.ts";

interface OrganizationDetailsFormOptions
{
    organization: OrganizationDetailedDto | null;
    onDismiss: () => void;
    onComplete: (updatedOrganization: OrganizationDetailedDto) => void;
}

export const useOrganizationDetailsForm = ({organization, onDismiss, onComplete}: OrganizationDetailsFormOptions) =>
{
    const {token} = useAuth();
    const [organizationName, setOrganizationName] = useState(organization?.name || "");
    const [registrationNumber, setRegistrationNumber] = useState(organization?.registrationNumber || "");
    const [savingData, setSavingData] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const onClose = () =>
    {
        setOrganizationName(organization?.name || "");
        setRegistrationNumber(organization?.registrationNumber || "");
        setError(null);
        onDismiss();
    };

    const handleSave = async () =>
    {
        if (!organization?.id) return;

        if (!organizationName.trim())
        {
            setError("Organization name is required");
            return;
        }

        if (!registrationNumber.trim())
        {
            setError("Registration number is required");
            return;
        }

        setSavingData(true);
        setError(null);

        try
        {
            await updateOrganization(
                organization.id,
                {
                    name: organizationName.trim(),
                    registrationNumber: registrationNumber.trim()
                },
                token || undefined
            );

            const updatedOrganization = {
                ...organization,
                name: organizationName.trim(),
                registrationNumber: registrationNumber.trim()
            };

            onComplete(updatedOrganization);
            onClose();
        }
        catch (err: unknown)
        {
            setError(extractDialogErrorMessage(err, "Something went wrong. Please try again in a moment."));
            console.error("Failed to update organization details:", err);
        }
        finally
        {
            setSavingData(false);
        }
    };

    const hasChanges =
        organizationName !== organization?.name ||
        registrationNumber !== organization?.registrationNumber;

    return {
        organizationName,
        setOrganizationName,
        registrationNumber,
        setRegistrationNumber,
        savingData,
        error,
        hasChanges,
        handleSave,
        onClose,
    };
};
