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
                <DialogBody id={"organization-picker-dialog-dialog-body-1"}>
                    <DialogTitle id={"organization-picker-dialog-dialog-title-1"}>
                        Select an organization
                    </DialogTitle>
                    <DialogContent id={"organization-picker-dialog-dialog-content-1"}
                                   className={styles.content}>
                        <Text id={"organization-picker-dialog-text-1"}>
                            You belong to more than one organization. Choose the one you want to work in.
                        </Text>
                        <div id={"organization-picker-dialog-div-1"}
                             className={styles.orgList}>
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
