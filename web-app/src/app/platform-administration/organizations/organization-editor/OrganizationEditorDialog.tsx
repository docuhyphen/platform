import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
} from "@fluentui/react-components";
import {PlatformOrganizationSummary} from "../../../../services/types/platformOrganizations.ts";
import OrganizationEditorForm, {OrganizationEditorSaveContent} from "./OrganizationEditorForm.tsx";
import {useOrganizationEditorStyles} from "./OrganizationEditorStyles.tsx";
import {useOrganizationEditor} from "./useOrganizationEditor.ts";
import {useAuth} from "../../../../context/AuthContext.tsx";

interface OrganizationEditorDialogProps
{
    organization: PlatformOrganizationSummary | null;
    onDismiss: () => void;
    onSaved: () => void;
}

const OrganizationEditorDialog = ({organization, onDismiss, onSaved}: OrganizationEditorDialogProps) =>
{
    const styles = useOrganizationEditorStyles();
    const {refreshCurrentSession} = useAuth();
    const editor = useOrganizationEditor(organization, onSaved, refreshCurrentSession);

    return (
        <Dialog
            open={organization !== null}
            onOpenChange={(_, data) => !data.open && !editor.saving && onDismiss()}>
            <DialogSurface id={"platform-organization-editor-surface"}>
                <DialogBody
                    id={"platform-organization-editor-body"}
                    className={styles.dialogBody}>
                    <DialogTitle id={"platform-organization-editor-title"}>
                        Edit {organization?.name ?? "organization"}
                    </DialogTitle>
                    <DialogContent
                        id={"platform-organization-editor-content"}
                        className={styles.content}>
                        <OrganizationEditorForm editor={editor}/>
                    </DialogContent>
                    <DialogActions
                        id={"platform-organization-editor-actions"}
                        className={styles.footer}>
                        <Button
                            id={"platform-organization-editor-save"}
                            appearance={"primary"}
                            shape={"circular"}
                            disabled={editor.saving}
                            onClick={() => void editor.save()}>
                            <OrganizationEditorSaveContent saving={editor.saving}/>
                        </Button>
                        <Button
                            id={"platform-organization-editor-cancel"}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={editor.saving}
                            onClick={onDismiss}>
                            Cancel
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default OrganizationEditorDialog;
