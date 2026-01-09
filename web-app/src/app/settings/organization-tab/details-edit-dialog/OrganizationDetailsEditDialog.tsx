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
    const {token} = useAuth();
    const [organizationName, setOrganizationName] = useState(organization?.name || "");
    const [registrationNumber, setRegistrationNumber] = useState(organization?.registrationNumber || "");
    const [savingData, setSavingData] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleSave = async () =>
    {
        if (!organization?.id) return;

        setSavingData(true);
        setError(null);

        try
        {
            await updateOrganization(
                organization.id,
                {
                    name: organizationName,
                    registrationNumber: registrationNumber
                },
                token || undefined
            );

            const updatedOrganization = {
                ...organization,
                name: organizationName,
                registrationNumber: registrationNumber
            };

            onComplete(updatedOrganization);
            onClose();
        }
        catch (err: any)
        {
            setError(err.message || "Failed to update organization details");
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
                        {error && <div style={{color: 'red', marginBottom: '10px'}}>{error}</div>}

                        <Field label="Organization Name">
                            <Input
                                type="text"
                                value={organizationName}
                                maxLength={80}
                                onChange={(e) => setOrganizationName(e.target.value)}
                            />
                        </Field>

                        <Field label="Registration Number">
                            <Input
                                type="text"
                                value={registrationNumber}
                                maxLength={80}
                                onChange={(e) => setRegistrationNumber(e.target.value)}
                            />
                        </Field>
                    </DialogContent>
                </DialogBody>
                <DialogActions>
                    <Button
                        appearance="primary"
                        shape="circular"
                        disabled={savingData || !organizationName || !hasChanges}
                        onClick={handleSave}
                    >
                        {savingData && <Spinner size="tiny"/>}
                        Update
                    </Button>
                    <DialogTrigger disableButtonEnhancement>
                        <Button
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