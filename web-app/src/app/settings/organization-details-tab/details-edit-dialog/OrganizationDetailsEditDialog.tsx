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
import React, {useState} from "react";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {OrganizationDetailedDto} from "../../../models/models.tsx";
import {updateOrganization} from "../../../../services/organizationApi.ts";
import {useOrganizationEditDialogStyles} from "./OrganizationDetailsEditDialogStyles.tsx";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";

interface OrganizationDetailsEditDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    organization: OrganizationDetailedDto | null;
    onComplete: (updatedOrganization: OrganizationDetailedDto) => void;
}

const OrganizationDetailsEditDialog: React.FC<OrganizationDetailsEditDialogProps> = (
    {
        isOpen,
        onDismiss,
        organization,
        onComplete
    }) =>
{
    const styles = useOrganizationEditDialogStyles()
    const globalStyles = useGlobalStyles()
    const {token} = useAuth();
    const [organizationName, setOrganizationName] = useState(organization?.name || "");
    const [registrationNumber, setRegistrationNumber] = useState(organization?.registrationNumber || "");
    const [savingData, setSavingData] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const extractErrorMessage = (e: unknown): string =>
    {
        if (typeof e === "string") return e;
        const error = e as { errorMessage?: string; message?: string; response?: { data?: { errorMessage?: string; message?: string } } };
        const msg = error.errorMessage || error.message || error.response?.data?.errorMessage || error.response?.data?.message;
        if (msg) return msg;
        return "Something went wrong. Please try again in a moment.";
    }

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
            setError(extractErrorMessage(err));
            console.error("Failed to update organization details:", err);
        }
        finally
        {
            setSavingData(false);
        }
    };

    const onClose = () =>
    {
        setOrganizationName(organization?.name || "");
        setRegistrationNumber(organization?.registrationNumber || "");
        setError(null);
        onDismiss();
    };

    const hasChanges =
        organizationName !== organization?.name ||
        registrationNumber !== organization?.registrationNumber;

    return (
        <Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Update Organization Details</DialogTitle>
                    <DialogContent className={styles.dialogContentContainer}>
                        { error && <div className={styles.errorContainer}>{error}</div>}

                        <Field label="Organization Name">
                            <Input
                                id={"input-org-name"}
                                type="text"
                                value={organizationName}
                                maxLength={120}
                                onChange={(e) => setOrganizationName(e.target.value)}
                                onKeyDown={(e) => { if (e.key === "Enter") handleSave(); }}
                            />
                        </Field>

                        <Field label="Registration Number">
                            <Input
                                id={"input-reg-number"}
                                type="text"
                                value={registrationNumber}
                                maxLength={30}
                                onChange={(e) => setRegistrationNumber(e.target.value)}
                                onKeyDown={(e) => { if (e.key === "Enter") handleSave(); }}
                            />
                        </Field>
                    </DialogContent>
                </DialogBody>
                <DialogActions>
                    <Button
                        id={"button-update-org"}
                        appearance="primary"
                        shape="circular"
                        className={globalStyles.buttonWithLoading}
                        disabled={savingData || !organizationName.trim() || !registrationNumber.trim() || !hasChanges}
                        onClick={handleSave}
                    >
                        {savingData && <Spinner size="tiny"/>}
                        {savingData ? "Updating…" : "Update"}
                    </Button>
                    <DialogTrigger disableButtonEnhancement>
                        <Button
                            id={"button-close-org-edit"}
                            appearance="secondary"
                            shape="circular"
                            disabled={savingData}
                            onClick={onClose}
                        >
                            Close
                        </Button>
                    </DialogTrigger>
                </DialogActions>
            </DialogSurface>
        </Dialog>
    );
};

export default OrganizationDetailsEditDialog;