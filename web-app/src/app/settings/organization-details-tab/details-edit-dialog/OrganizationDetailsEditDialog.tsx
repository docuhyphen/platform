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
import {OrganizationDetailedDto} from "../../../models/models.tsx";
import {useOrganizationEditDialogStyles} from "./OrganizationDetailsEditDialogStyles.tsx";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {useOrganizationDetailsForm} from "./useOrganizationDetailsForm.ts";

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
    const styles = useOrganizationEditDialogStyles();
    const globalStyles = useGlobalStyles();
    const form = useOrganizationDetailsForm({organization, onDismiss, onComplete});

    return (
        <Dialog modalType="alert"
                open={isOpen}>
            <DialogSurface id={"organization-details-edit-dialog-dialog-surface-1"}>
                <DialogBody id={"organization-details-edit-dialog-dialog-body-1"}>
                    <DialogTitle id={"organization-details-edit-dialog-dialog-title-1"}>Update Organization Details</DialogTitle>
                    <DialogContent id={"organization-details-edit-dialog-dialog-content-1"}
                                   className={styles.dialogContentContainer}>
                        {form.error && <div id={"organization-details-edit-dialog-div-1"}
                                            className={styles.errorContainer}>{form.error}</div>}

                        <Field id={"organization-details-edit-dialog-field-1"}
                               label="Organization Name">
                            <Input
                                id={"input-org-name"}
                                type="text"
                                value={form.organizationName}
                                maxLength={120}
                                onChange={(e) => form.setOrganizationName(e.target.value)}
                                onKeyDown={(e) => { if (e.key === "Enter") form.handleSave(); }}
                            />
                        </Field>

                        <Field id={"organization-details-edit-dialog-field-2"}
                               label="Registration Number">
                            <Input
                                id={"input-reg-number"}
                                type="text"
                                value={form.registrationNumber}
                                maxLength={30}
                                onChange={(e) => form.setRegistrationNumber(e.target.value)}
                                onKeyDown={(e) => { if (e.key === "Enter") form.handleSave(); }}
                            />
                        </Field>
                    </DialogContent>
                </DialogBody>
                <DialogActions id={"organization-details-edit-dialog-dialog-actions-1"}>
                    <Button
                        id={"button-update-org"}
                        appearance="primary"
                        shape="circular"
                        className={globalStyles.buttonWithLoading}
                        disabled={form.savingData || !form.organizationName.trim() || !form.registrationNumber.trim() || !form.hasChanges}
                        onClick={form.handleSave}
                    >
                        {form.savingData && <Spinner id={"organization-details-edit-dialog-spinner-1"}
                                                     size="tiny"/>}
                        {form.savingData ? "Updating…" : "Update"}
                    </Button>
                    <DialogTrigger disableButtonEnhancement>
                        <Button
                            id={"button-close-org-edit"}
                            appearance="secondary"
                            shape="circular"
                            disabled={form.savingData}
                            onClick={form.onClose}
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
