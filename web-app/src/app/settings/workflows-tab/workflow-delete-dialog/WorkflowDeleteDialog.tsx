import React from "react";
import {WorkflowDefinitionSummaryDto} from "../../../models/models.tsx";
import {
    Dialog,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    MessageBar,
    MessageBarBody,
    Text,
} from "@fluentui/react-components";
import WorkflowDeleteDialogActions from "./WorkflowDeleteDialogActions.tsx";
import {useWorkflowDeletion} from "./useWorkflowDeletion.ts";

interface WorkflowDeleteDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    definition: WorkflowDefinitionSummaryDto;
    onDeleted: () => void;
}

const WorkflowDeleteDialog: React.FC<WorkflowDeleteDialogProps> = (
    {
        isOpen,
        onDismiss,
        definition,
        onDeleted,
    }) =>
{
    const {
        deleting,
        deleteStarted,
        countdown,
        errorMessage,
        startDeleteCountdown,
        cancelDeleteCountdown,
    } = useWorkflowDeletion({isOpen, definition, onDeleted, onDismiss});

    return (
        <Dialog modalType="alert"
                open={isOpen}>
            <DialogSurface id={"workflow-delete-dialog-dialog-surface-1"}>
                <DialogBody id={"workflow-delete-dialog-dialog-body-1"}>
                    <DialogTitle id={"workflow-delete-dialog-dialog-title-1"}>Deleting {definition?.name}</DialogTitle>
                    <DialogContent id={"workflow-delete-dialog-dialog-content-1"}>
                        {errorMessage && (
                            <MessageBar id={"workflow-delete-dialog-message-bar-1"}
                                        intent="error">
                                <MessageBarBody id={"workflow-delete-dialog-message-bar-body-1"}>
                                    <Text id={"workflow-delete-dialog-text-1"}
                                          size={200}>{errorMessage}</Text>
                                </MessageBarBody>
                            </MessageBar>
                        )}
                        {deleteStarted ? (
                            <div id={"workflow-delete-dialog-div-1"}>Deleting in {countdown} seconds...</div>
                        ) : (
                            <div id={"workflow-delete-dialog-div-2"}>Are you sure you want to delete this workflow?</div>
                        )}
                    </DialogContent>
                    <WorkflowDeleteDialogActions
                        deleteStarted={deleteStarted}
                        deleting={deleting}
                        onCancelCountdown={cancelDeleteCountdown}
                        onDelete={startDeleteCountdown}
                        onDismiss={onDismiss}
                    />
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default WorkflowDeleteDialog;
