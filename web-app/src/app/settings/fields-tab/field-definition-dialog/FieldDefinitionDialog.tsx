import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Spinner,
    Tooltip,
} from '@fluentui/react-components';
import {QuestionCircleRegular} from '@fluentui/react-icons';
import {FieldScopeKind} from '../../../models/models';
import {useHelpSidebar} from '../../../../context/HelpSidebarContext';
import FieldDefinitionFormBody from '../field-definition-form-body/FieldDefinitionFormBody';
import {useFieldDefinitionForm} from './useFieldDefinitionForm';
import {useFieldDefinitionDialogStyles} from './FieldDefinitionDialogStyles';

interface Props
{
    open: boolean;
    onClose: () => void;
    onSaved: () => void;
    existingNamespaces?: string[];
    enforcedScope?: FieldScopeKind;
}

const FieldDefinitionDialog = ({open, onClose, onSaved, existingNamespaces = [], enforcedScope}: Props) =>
{
    const styles = useFieldDefinitionDialogStyles();
    const {openHelpArticle} = useHelpSidebar();
    const {form, saving, error, update, save} = useFieldDefinitionForm(open, enforcedScope, onSaved);

    return (
        <Dialog open={open}
                onOpenChange={(_, d) => { if (!d.open) onClose(); }}>
            <DialogSurface id={"field-definition-dialog-dialog-surface-1"}>
                <DialogBody id={"field-definition-dialog-dialog-body-1"}>
                    <DialogTitle id={"field-definition-dialog-dialog-title-1"}
                                 className={styles.dialogTitle}>
                        <span id={"field-definition-dialog-span-1"}> New field </span>
                        <div id={"field-definition-dialog-div-1"}>
                            <Tooltip content="View fields help article"
                                     relationship="description">
                                <Button id="field-def-help"
                                        appearance="subtle"
                                        shape="circular"
                                        size="small"
                                        icon={<QuestionCircleRegular/>}
                                        onClick={() => openHelpArticle('fields-overview')}/>
                            </Tooltip>
                        </div>
                    </DialogTitle>
                    <DialogContent id={"field-definition-dialog-dialog-content-1"}>
                        <FieldDefinitionFormBody form={form}
                                                 update={update}
                                                 error={error}
                                                 existingNamespaces={existingNamespaces}/>
                    </DialogContent>
                    <DialogActions id={"field-definition-dialog-dialog-actions-1"}>
                        <Button id="field-def-save"
                                appearance="primary"
                                shape="circular"
                                disabled={saving}
                                icon={saving ? <Spinner id={"field-definition-dialog-spinner-1"}
                                                        size="tiny"/> : undefined}
                                onClick={save}>
                            {saving ? 'Saving...' : 'Create'}
                        </Button>
                        <Button id="field-def-cancel"
                                appearance="secondary"
                                shape="circular"
                                disabled={saving}
                                onClick={onClose}>
                            Cancel
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default FieldDefinitionDialog;
