import React, {useEffect, useState} from "react";
import {WorkflowDefinitionSummaryDto} from "../../models/models.tsx";
import {useGlobalStyles} from "../../../GlobalStyles.tsx";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    MessageBar,
    MessageBarBody,
    Spinner,
    Text,
} from "@fluentui/react-components";
import {deleteWorkflowDefinition} from "../../../services/workflowService.ts";

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
    const globalStyles = useGlobalStyles();
    const [deleting, setDeleting] = React.useState(false);
    const [deleteStarted, setDeleteStarted] = React.useState(false);
    const [countdown, setCountdown] = React.useState(10);
    const timerRef = React.useRef<NodeJS.Timeout | null>(null);
    const [dialogErrorMessage, setDialogErrorMessage] = useState<string | null>(null);

    useEffect(() =>
    {
        if (isOpen) setDialogErrorMessage(null);
    }, [isOpen]);

    const onDelete = () =>
    {
        setDeleteStarted(true);
        setCountdown(10);

        timerRef.current = setInterval(() =>
        {
            setCountdown(prev =>
            {
                if (prev <= 1)
                {
                    clearInterval(timerRef.current!);
                    completeDeletion();
                    return 0;
                }
                return prev - 1;
            });
        }, 1000);
    };

    const onCancel = () =>
    {
        if (timerRef.current)
        {
            clearInterval(timerRef.current);
        }
        setDeleteStarted(false);
        setCountdown(10);
    };

    const completeDeletion = async () =>
    {
        setDeleting(true);
        try
        {
            await deleteWorkflowDefinition(definition.id);
            onDeleted();
            onDismiss();
        }
        catch (error)
        {
            setDialogErrorMessage("Error deleting workflow");
            console.error("Error deleting workflow:", error);
        }
        finally
        {
            setDeleting(false);
            setDeleteStarted(false);
        }
    };

    return (
        <Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Deleting {definition?.name}</DialogTitle>
                    <DialogContent>
                        {dialogErrorMessage && (
                            <MessageBar intent="error">
                                <MessageBarBody>
                                    <Text size={200}>{dialogErrorMessage}</Text>
                                </MessageBarBody>
                            </MessageBar>
                        )}
                        {deleteStarted ? (
                            <div>Deleting in {countdown} seconds...</div>
                        ) : (
                            <div>Are you sure you want to delete this workflow?</div>
                        )}
                    </DialogContent>
                    <DialogActions>
                        {deleteStarted ? (
                            <Button
                                appearance="primary"
                                className={globalStyles.buttonWithLoading}
                                shape="circular"
                                onClick={onCancel}>
                                Cancel
                            </Button>
                        ) : (
                            <>
                                <Button
                                    appearance="primary"
                                    className={globalStyles.buttonWithLoading}
                                    shape="circular"
                                    onClick={onDelete}>
                                    {deleting && <Spinner size="tiny"/>}
                                    Yes, Delete
                                </Button>
                                <DialogTrigger disableButtonEnhancement>
                                    <Button
                                        appearance="secondary"
                                        shape="circular"
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
    );
};

export default WorkflowDeleteDialog;
