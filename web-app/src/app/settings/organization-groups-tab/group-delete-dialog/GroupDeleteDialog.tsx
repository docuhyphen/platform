import React from "react";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Spinner,
} from "@fluentui/react-components";
import {useGroupDeleteDialogStyles} from "./GroupDeleteDialogStyles.tsx";
import {useGroupDeleteCountdown} from "./useGroupDeleteCountdown.ts";
import {OrganizationGroupDetailedDto} from "../../../models/models.tsx";

interface GroupDeleteDialog
{
    organizationId: string,
    isOpen: boolean;
    onDismiss: () => void;
    group: OrganizationGroupDetailedDto | null;
    onDeleted: (groupId: string) => void;
}

const GroupDeleteDialog: React.FC<GroupDeleteDialog> = (
    {
        organizationId,
        isOpen,
        onDismiss,
        group,
        onDeleted
    }) =>
{
    const globalStyles = useGlobalStyles();
    const deleteDialogStyles = useGroupDeleteDialogStyles();
    const {deleting, deleteStarted, countdown, error, onDelete, onCancel} =
        useGroupDeleteCountdown({organizationId, group, onDeleted, onDismiss});

    return <>
        {<Dialog modalType="alert"
                 open={isOpen}>
            <DialogSurface id={"group-delete-dialog-dialog-surface-1"}>
                <DialogBody id={"group-delete-dialog-dialog-body-1"}>
                    <DialogTitle id={"group-delete-dialog-dialog-title-1"}>Deleting {group && group.name}</DialogTitle>
                    <DialogContent id={"group-delete-dialog-dialog-content-1"}>
                        <div id={"group-delete-dialog-div-1"}
                             className={deleteDialogStyles.errorMessage}>{error || " "}</div>
                        {deleteStarted ? (
                            <div id={"group-delete-dialog-div-2"}>
                                Deleting in {countdown} seconds...
                            </div>
                        ) : (
                            <div id={"group-delete-dialog-div-3"}>
                                Are you sure you want to delete this group?
                            </div>
                        )}
                    </DialogContent>
                    <DialogActions id={"group-delete-dialog-dialog-actions-1"}>
                        {deleteStarted ? (
                            <Button
                                id="group-delete-dialog-cancel-countdown"
                                appearance="primary"
                                className={globalStyles.buttonWithLoading}
                                shape={"circular"}
                                onClick={onCancel}>
                                Cancel
                            </Button>
                        ) : (
                            <>
                                <Button
                                    id="group-delete-dialog-confirm-delete"
                                    appearance="primary"
                                    className={globalStyles.buttonWithLoading}
                                    shape={"circular"}
                                    onClick={onDelete}>
                                    {deleting && <Spinner id={"group-delete-dialog-spinner-1"}
                                                          size={"tiny"}/>}
                                    Yes, Delete
                                </Button>
                                <DialogTrigger disableButtonEnhancement>
                                    <Button
                                        id="group-delete-dialog-no-cancel"
                                        appearance="secondary"
                                        shape={"circular"}
                                        disabled={deleting}
                                        onClick={onDismiss}>
                                        No, Cancel
                                    </Button>
                                </DialogTrigger>
                            </>
                        )}
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
        }
    </>
}

export default GroupDeleteDialog;
