import {Button, DialogActions, Spinner} from '@fluentui/react-components';

interface Props
{
    showSave: boolean;
    saving: boolean;
    isEdit: boolean;
    onSave: () => void;
    onCancel: () => void;
}

const CommunicationEditorActions = ({showSave, saving, isEdit, onSave, onCancel}: Props) =>
(
    <DialogActions id={"communication-editor-dialog-dialog-actions-1"}>
        {showSave && (
            <Button
                id={"button-comm-save"}
                appearance="primary"
                shape={"circular"}
                onClick={onSave}
                disabled={saving}
            >
                {saving ? <><Spinner id={"communication-editor-dialog-spinner-2"}
                                     size="tiny"/> Saving…</> : (isEdit ? 'Save Changes' : 'Create Communication')}
            </Button>
        )}
        <Button
            id={"button-comm-cancel"}
            appearance={"secondary"}
            shape={"circular"}
            onClick={onCancel}
            disabled={saving}
        >Cancel</Button>
    </DialogActions>
);

export default CommunicationEditorActions;
