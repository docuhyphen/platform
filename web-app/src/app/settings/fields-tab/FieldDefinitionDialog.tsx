import {useEffect, useState} from 'react';
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
import {FieldDataClassification, FieldValueType} from '../../models/models';
import {createFieldDefinition, FieldContractRequest} from '../../../services/fieldsService';
import {typeSupportsOptions} from './fieldLabels';
import FieldDefinitionFormBody, {FieldForm} from './FieldDefinitionFormBody';
import {useFieldsTabStyles} from './FieldsTabStyles';
import {useHelpSidebar} from '../../../context/HelpSidebarContext';

interface Props
{
    open: boolean;
    onClose: () => void;
    onSaved: () => void;
    existingNamespaces?: string[];
}

const emptyForm = (): FieldForm => ({
    namespace: '',
    fieldKey: '',
    valueType: FieldValueType.SHORT_TEXT,
    label: '',
    helpText: '',
    classification: FieldDataClassification.INTERNAL,
    options: [],
});

const FieldDefinitionDialog = ({open, onClose, onSaved, existingNamespaces = []}: Props) =>
{
    const styles = useFieldsTabStyles();
    const {openHelpArticle} = useHelpSidebar();
    const [form, setForm] = useState<FieldForm>(emptyForm());
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() =>
    {
        if (open) { setForm(emptyForm()); setError(null); }
    }, [open]);

    const update = (patch: Partial<FieldForm>) => setForm(prev => ({...prev, ...patch}));

    const handleSave = async () =>
    {
        if (!form.namespace.trim() || !form.fieldKey.trim() || !form.label.trim())
        {
            setError('Namespace, key, and label are required');
            return;
        }
        const withOptions = typeSupportsOptions(form.valueType);
        if (withOptions && form.options.filter(o => o.code.trim()).length === 0)
        {
            setError('Add at least one option for a selection field');
            return;
        }
        setSaving(true);
        setError(null);
        const contract: FieldContractRequest = {
            valueType: form.valueType,
            label: form.label.trim(),
            helpText: form.helpText.trim() || undefined,
            dataClassification: form.classification,
            options: withOptions ? form.options.filter(o => o.code.trim()) : [],
        };
        try
        {
            await createFieldDefinition({
                namespace: form.namespace.trim(),
                fieldKey: form.fieldKey.trim(),
                contract,
            });
            onSaved();
        }
        catch (e: unknown)
        {
            const msg = typeof e === 'object' && e !== null && 'error' in e
                ? String((e as {error: string}).error)
                : 'Failed to create field';
            setError(msg);
        }
        finally
        {
            setSaving(false);
        }
    };

    return (
        <Dialog open={open}
                onOpenChange={(_, d) => { if (!d.open) onClose(); }}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle  className={styles.dialogTitle}>
                        <span> New field </span>
                        <div>
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
                    <DialogContent>
                        <FieldDefinitionFormBody form={form}
                                                 update={update}
                                                 error={error}
                                                 existingNamespaces={existingNamespaces}/>
                    </DialogContent>
                    <DialogActions>
                        <Button id="field-def-cancel"
                                appearance="secondary"
                                shape="circular"
                                disabled={saving}
                                onClick={onClose}>
                            Cancel
                        </Button>
                        <Button id="field-def-save"
                                appearance="primary"
                                shape="circular"
                                disabled={saving}
                                icon={saving ? <Spinner size="tiny"/> : undefined}
                                onClick={handleSave}>
                            {saving ? 'Saving...' : 'Create'}
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default FieldDefinitionDialog;
