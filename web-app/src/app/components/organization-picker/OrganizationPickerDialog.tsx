import React from "react";
import {
    Button,
    Dialog,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Text,
} from "@fluentui/react-components";
import {SessionOrganizationOptionDto} from "../../models/models.tsx";
import {useOrganizationPickerDialogStyles} from "./OrganizationPickerDialogStyles.tsx";

interface OrganizationPickerDialogProps
{
    isOpen: boolean;
    organizations: SessionOrganizationOptionDto[];
    onSelect: (organizationId: string) => void;
}

/**
 * Presentation-only dialog that asks the caller to pick which organization to act within.
 * Non-dismissible while open: there is no sensible cancel when a choice is required.
 */
const OrganizationPickerDialog: React.FC<OrganizationPickerDialogProps> = (
    {
        isOpen,
        organizations,
        onSelect,
    }) =>
{
    const styles = useOrganizationPickerDialogStyles();

    return (
        <Dialog modalType="alert"
                open={isOpen}>
            <DialogSurface id={"organization-picker-dialog-surface"}>
                <DialogBody>
                    <DialogTitle>
                        Select an organization
                    </DialogTitle>
                    <DialogContent className={styles.content}>
                        <Text>
                            You belong to more than one organization. Choose the one you want to work in.
                        </Text>
                        <div className={styles.orgList}>
                            {organizations.map(org => (
                                <Button id={`organization-picker-option-${org.organizationId}`}
                                        key={org.organizationId}
                                        appearance="outline"
                                        shape={"circular"}
                                        className={styles.orgButton}
                                        onClick={() => onSelect(org.organizationId)}>
                                    {org.name}
                                </Button>
                            ))}
                        </div>
                    </DialogContent>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default OrganizationPickerDialog;
