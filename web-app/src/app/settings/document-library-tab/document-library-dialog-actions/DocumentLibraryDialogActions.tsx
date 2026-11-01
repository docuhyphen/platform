import {Button, DialogActions, Spinner} from '@fluentui/react-components';
import {useDocumentLibraryDialogActionsStyles} from './DocumentLibraryDialogActionsStyles.tsx';

export interface DocumentLibraryDialogActionIds
{
    actions: string;
    container: string;
    confirm: string;
    cancel: string;
    spinner: string;
}

interface DocumentLibraryDialogActionsProps
{
    ids: DocumentLibraryDialogActionIds;
    confirmLabel: string;
    pendingLabel: string;
    pending: boolean;
    confirmDisabled?: boolean;
    onConfirm: () => void;
    onCancel: () => void;
}

const DocumentLibraryDialogActions = ({
    ids,
    confirmLabel,
    pendingLabel,
    pending,
    confirmDisabled,
    onConfirm,
    onCancel,
}: DocumentLibraryDialogActionsProps) =>
{
    const styles = useDocumentLibraryDialogActionsStyles();

    return (
        <DialogActions id={ids.actions}>
            <div id={ids.container}
                 className={styles.dialogActions}>
                <Button id={ids.confirm}
                        appearance="primary"
                        shape="circular"
                        onClick={onConfirm}
                        disabled={confirmDisabled ?? pending}
                        icon={pending ? <Spinner id={ids.spinner}
                                                 size="tiny"/> : undefined}>
                    {pending ? pendingLabel : confirmLabel}
                </Button>
                <Button id={ids.cancel}
                        appearance="secondary"
                        shape="circular"
                        onClick={onCancel}
                        disabled={pending}>
                    Cancel
                </Button>
            </div>
        </DialogActions>
    );
};

export default DocumentLibraryDialogActions;
