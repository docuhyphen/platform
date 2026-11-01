import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
} from "@fluentui/react-components";

interface Props
{
    open: boolean;
    onOpenChange: (open: boolean) => void;
    onDiscard: () => void;
    onKeepEditing: () => void;
}

const WorkflowDiscardDialog = ({open, onOpenChange, onDiscard, onKeepEditing}: Props) => (
    <Dialog open={open}
            onOpenChange={(_, d) => onOpenChange(d.open)}>
        <DialogSurface id={"workflow-designer-container-dialog-surface"}>
            <DialogBody id={"workflow-designer-container-dialog-body"}>
                <DialogTitle id={"workflow-designer-discard-changes-dialog-title"}>Discard
                    changes?</DialogTitle>
                <DialogContent id={"workflow-designer-container-dialog-content"}>
                    You have unsaved changes. If you leave now they will be lost.
                </DialogContent>
                <DialogActions id={"workflow-designer-container-dialog-actions"}>
                    <Button
                        id="workflow-designer-discard-btn"
                        appearance="primary"
                        shape={"circular"}
                        onClick={onDiscard}
                    >
                        Discard
                    </Button>
                    <Button
                        id="workflow-designer-keep-editing-btn"
                        appearance="secondary"
                        shape={"circular"}
                        onClick={onKeepEditing}
                    >
                        Keep editing
                    </Button>
                </DialogActions>
            </DialogBody>
        </DialogSurface>
    </Dialog>
);

export default WorkflowDiscardDialog;
