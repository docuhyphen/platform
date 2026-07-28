import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
} from "@fluentui/react-components";
import {BlueprintDefinitionSummaryDto} from "../../models/models.tsx";

interface Props
{
    blueprint: BlueprintDefinitionSummaryDto;
    deleting: boolean;
    onConfirm: () => void;
    onDismiss: () => void;
}

const PlatformBlueprintDeleteDialog = (
    {
        blueprint,
        deleting,
        onConfirm,
        onDismiss,
    }: Props) => (
    <Dialog
        open={true}
        onOpenChange={(_, data) =>
        {
            if (!data.open) onDismiss();
        }}>
        <DialogSurface id={"platform-blueprint-delete-dialog"}>
            <DialogBody>
                <DialogTitle id={"platform-blueprint-delete-title"}>
                    Delete platform Blueprint
                </DialogTitle>
                <DialogContent id={"platform-blueprint-delete-content"}>
                    Delete {blueprint.name}? This permanently removes the platform Blueprint.
                </DialogContent>
                <DialogActions id={"platform-blueprint-delete-actions"}>
                    <Button
                        id={"platform-blueprint-delete-confirm"}
                        appearance={"primary"}
                        shape={"circular"}
                        disabled={deleting}
                        onClick={onConfirm}>
                        Delete
                    </Button>
                    <Button
                        id={"platform-blueprint-delete-cancel"}
                        appearance={"secondary"}
                        shape={"circular"}
                        disabled={deleting}
                        onClick={onDismiss}>
                        Cancel
                    </Button>
                </DialogActions>
            </DialogBody>
        </DialogSurface>
    </Dialog>
);

export default PlatformBlueprintDeleteDialog;
