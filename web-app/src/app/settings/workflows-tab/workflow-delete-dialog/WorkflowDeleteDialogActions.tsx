import {Button, DialogActions, DialogTrigger, Spinner} from "@fluentui/react-components";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";

interface Props
{
    deleteStarted: boolean;
    deleting: boolean;
    onCancelCountdown: () => void;
    onDelete: () => void;
    onDismiss: () => void;
}

const WorkflowDeleteDialogActions = ({deleteStarted, deleting, onCancelCountdown, onDelete, onDismiss}: Props) =>
{
    const globalStyles = useGlobalStyles();

    return (
        <DialogActions id={"workflow-delete-dialog-dialog-actions-1"}>
            {deleteStarted ? (
                <Button
                    id="workflow-delete-dialog-cancel-btn"
                    appearance="primary"
                    className={globalStyles.buttonWithLoading}
                    shape="circular"
                    onClick={onCancelCountdown}
                >
                    Cancel
                </Button>
            ) : (
                <>
                    <Button
                        id="workflow-delete-dialog-confirm-btn"
                        appearance="primary"
                        className={globalStyles.buttonWithLoading}
                        shape="circular"
                        onClick={onDelete}
                    >
                        {deleting && <Spinner id={"workflow-delete-dialog-spinner-1"}
                                              size="tiny"/>}
                        Yes, Delete
                    </Button>
                    <DialogTrigger disableButtonEnhancement>
                        <Button
                            id="workflow-delete-dialog-dismiss-btn"
                            appearance="secondary"
                            shape="circular"
                            disabled={deleting}
                            onClick={onDismiss}
                        >
                            No, Cancel
                        </Button>
                    </DialogTrigger>
                </>
            )}
        </DialogActions>
    );
};

export default WorkflowDeleteDialogActions;
