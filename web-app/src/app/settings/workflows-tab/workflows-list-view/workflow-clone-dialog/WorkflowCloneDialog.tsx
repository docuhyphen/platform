import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Input,
    Label,
} from "@fluentui/react-components";
import {useWorkflowCloneDialogStyles} from "./WorkflowCloneDialogStyles.tsx";

interface Props
{
    open: boolean;
    name: string;
    cloning: boolean;
    onNameChange: (value: string) => void;
    onConfirm: () => void;
    onClose: () => void;
}

const WorkflowCloneDialog = ({open, name, cloning, onNameChange, onConfirm, onClose}: Props) =>
{
    const styles = useWorkflowCloneDialogStyles();

    return (
        <Dialog open={open}
                onOpenChange={(_, d) => { if (!d.open) onClose(); }}>
            <DialogSurface id={"workflows-list-view-dialog-surface-1"}>
                <DialogBody id={"workflows-list-view-dialog-body-1"}>
                    <DialogTitle id={"workflows-list-view-dialog-title-1"}>Clone workflow</DialogTitle>
                    <DialogContent id={"workflows-list-view-dialog-content-1"}>
                        <Label
                            id={"workflows-list-view-label-1"}
                            htmlFor="clone-name-input"
                            className={styles.cloneNameLabel}
                        >
                            Name
                        </Label>
                        <Input
                            id="clone-name-input"
                            value={name}
                            onChange={(_, d) => onNameChange(d.value)}
                            onKeyDown={e => { if (e.key === "Enter") onConfirm(); }}
                            className={styles.cloneNameInput}
                            autoFocus
                        />
                    </DialogContent>
                    <DialogActions id={"workflows-list-view-dialog-actions-1"}>
                        <Button
                            id="workflows-list-clone-confirm-btn"
                            appearance="primary"
                            shape="circular"
                            onClick={onConfirm}
                            disabled={cloning || !name.trim()}
                        >
                            {cloning ? "Cloning..." : "Clone"}
                        </Button>
                        <Button
                            id="workflows-list-clone-cancel-btn"
                            appearance="secondary"
                            shape="circular"
                            onClick={onClose}
                        >
                            Cancel
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default WorkflowCloneDialog;
