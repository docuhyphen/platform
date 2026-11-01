import {Button, DialogActions, Spinner} from '@fluentui/react-components';

interface BlueprintEditorActionsProps
{
    saving: boolean;
    isEdit: boolean;
    onSave: () => void;
    onCancel: () => void;
}

const BlueprintEditorActions = ({saving, isEdit, onSave, onCancel}: BlueprintEditorActionsProps) => (
    <DialogActions id={"blueprint-editor-dialog-actions"}>
        <Button
            id={"button-blueprint-save"}
            appearance="primary"
            shape={"circular"}
            onClick={onSave}
            disabled={saving}
        >
            {saving ? <><Spinner
                id={"blueprint-editor-spinner"}
                size="tiny"/> Saving...</> : (isEdit ? 'Save Changes' : 'Create Blueprint')}
        </Button>
        <Button
            id={"button-blueprint-cancel"}
            appearance="secondary"
            shape={"circular"}
            onClick={onCancel}
            disabled={saving}
        >Cancel</Button>
    </DialogActions>
);

export default BlueprintEditorActions;
