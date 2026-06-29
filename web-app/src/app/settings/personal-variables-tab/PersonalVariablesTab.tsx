import {forwardRef, useEffect, useImperativeHandle, useState} from 'react';
import {
    Badge,
    Button,
    Drawer,
    DrawerBody,
    DrawerHeader,
    DrawerHeaderTitle,
    Field,
    Input,
    Menu,
    MenuList,
    MenuItem,
    MenuPopover,
    MenuTrigger,
    Spinner,
    Text,
} from '@fluentui/react-components';
import {DeleteRegular, EditRegular, MoreVerticalRegular} from '@fluentui/react-icons';
import {CreateVariableRequest, UpdateVariableRequest, VariableDefinitionDto, ViewMode} from '../../models/models';
import {createVariable, deleteVariable, listVariables, updateVariable} from '../../../services/variableService';
import {usePersonalVariablesTabStyles} from './PersonalVariablesTabStyles';

interface DrawerState
{
    open: boolean;
    editing?: VariableDefinitionDto;
}

export interface PersonalVariablesTabHandle
{
    openCreate: () => void;
}

interface PersonalVariablesTabProps
{
    viewMode?: ViewMode;
}

const PersonalVariablesTab = forwardRef<PersonalVariablesTabHandle, PersonalVariablesTabProps>(({viewMode = 'cards'}, ref) =>
{
    const styles = usePersonalVariablesTabStyles();
    const [variables, setVariables] = useState<VariableDefinitionDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [drawer, setDrawer] = useState<DrawerState>({open: false});
    const [formKey, setFormKey] = useState('');
    const [formValue, setFormValue] = useState('');
    const [formError, setFormError] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);

    const load = () =>
    {
        setLoading(true);
        setError(null);
        listVariables('PERSONAL')
            .then(setVariables)
            .catch(() => setError('Failed to load personal variables'))
            .finally(() => setLoading(false));
    };

    useEffect(() => { load(); }, []);

    const openCreate = () =>
    {
        setFormKey('');
        setFormValue('');
        setFormError(null);
        setDrawer({open: true});
    };

    useImperativeHandle(ref, () => ({openCreate}));

    const openEdit = (v: VariableDefinitionDto) =>
    {
        setFormKey(v.key);
        setFormValue(v.defaultValue ?? '');
        setFormError(null);
        setDrawer({open: true, editing: v});
    };

    const handleSave = async () =>
    {
        if (!formKey.trim()) { setFormError('Key is required'); return; }
        setSaving(true);
        setFormError(null);
        try
        {
            if (drawer.editing)
            {
                const req: UpdateVariableRequest = {defaultValue: formValue};
                await updateVariable(drawer.editing.id, req);
            }
            else
            {
                const req: CreateVariableRequest = {key: formKey.toUpperCase(), defaultValue: formValue, scope: 'PERSONAL'};
                await createVariable(req);
            }
            setDrawer({open: false});
            load();
        }
        catch (e: unknown)
        {
            const msg = typeof e === 'object' && e !== null && 'errorMessage' in e
                ? (e as {errorMessage: string}).errorMessage
                : 'Failed to save variable';
            setFormError(msg);
        }
        finally
        {
            setSaving(false);
        }
    };

    const handleDelete = async (v: VariableDefinitionDto) =>
    {
        await deleteVariable(v.id).catch(() => null);
        load();
    };

    return (
        <>
            <div className={styles.container}>
                <Text
                    size={300}
                    className={styles.descriptionText}
                >
                    Private key-value pairs only you can see and use. Override them when creating an exchange.
                </Text>

                {loading && <Spinner size="small" label="Loading…"/>}
                {!loading && error && (
                    <Text className={styles.errorText}>{error}</Text>
                )}
                {!loading && !error && variables.length === 0 && (
                    <Text className={styles.emptyText}>No personal variables yet.</Text>
                )}
                {!loading && !error && variables.length > 0 && viewMode === 'cards' && variables.map(v => (
                    <div
                        key={v.id}
                        className={styles.variableRow}
                    >
                        <div className={styles.variableRowInner}>
                            <code className={styles.codeKey}>{`{{${v.key}}}`}</code>
                            <Text
                                size={200}
                                className={styles.defaultValueText}
                            >
                                {v.defaultValue ? `Default: "${v.defaultValue}"` : <em>no default</em>}
                            </Text>
                            {!v.isActive && (
                                <Badge
                                    appearance="tint"
                                    color="severe"
                                    size="small"
                                >
                                    Inactive
                                </Badge>
                            )}
                        </div>
                        <Menu>
                            <MenuTrigger disableButtonEnhancement>
                                <Button
                                    id={`button-personal-var-menu-${v.id}`}
                                    size="small"
                                    appearance="subtle"
                                    shape={"circular"}
                                    icon={<MoreVerticalRegular/>}
                                />
                            </MenuTrigger>
                            <MenuPopover>
                                <MenuList>
                                    <MenuItem
                                        icon={<EditRegular/>}
                                        onClick={() => openEdit(v)}
                                    >
                                        Edit
                                    </MenuItem>
                                    <MenuItem
                                        icon={<DeleteRegular/>}
                                        onClick={() => handleDelete(v)}
                                    >
                                        Delete
                                    </MenuItem>
                                </MenuList>
                            </MenuPopover>
                        </Menu>
                    </div>
                ))}
                {!loading && !error && variables.length > 0 && viewMode === 'table' && (
                    <table className={styles.table}>
                        <thead>
                            <tr>
                                <th className={styles.th}>Token</th>
                                <th className={styles.th}>Default Value</th>
                                <th className={styles.th}>Active</th>
                                <th className={styles.th}/>
                            </tr>
                        </thead>
                        <tbody>
                            {variables.map(v => (
                                <tr key={v.id} className={styles.tr}>
                                    <td className={styles.td}><code className={styles.codeKey}>{`{{${v.key}}}`}</code></td>
                                    <td className={styles.td}><Text size={200}>{v.defaultValue || <em>no default</em>}</Text></td>
                                    <td className={styles.td}><Text size={200}>{v.isActive ? 'Active' : 'Inactive'}</Text></td>
                                    <td className={styles.td}>
                                        <Menu>
                                            <MenuTrigger disableButtonEnhancement>
                                                <Button size="small" appearance="subtle" shape="circular" icon={<MoreVerticalRegular/>}/>
                                            </MenuTrigger>
                                            <MenuPopover>
                                                <MenuList>
                                                    <MenuItem icon={<EditRegular/>} onClick={() => openEdit(v)}>Edit</MenuItem>
                                                    <MenuItem icon={<DeleteRegular/>} onClick={() => handleDelete(v)}>Delete</MenuItem>
                                                </MenuList>
                                            </MenuPopover>
                                        </Menu>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                )}
            </div>

            <Drawer
                open={drawer.open}
                onOpenChange={(_, d) => setDrawer(prev => ({...prev, open: d.open}))}
                position="end"
                size="small"
            >
                <DrawerHeader>
                    <DrawerHeaderTitle>{drawer.editing ? 'Edit Variable' : 'New Personal Variable'}</DrawerHeaderTitle>
                </DrawerHeader>
                <DrawerBody className={styles.drawerBody}>
                    <Field
                        label="Key"
                        required
                        hint="Uppercase alphanumeric. Used as {{KEY}} in templates."
                    >
                        <Input
                            id={"input-personal-var-key"}
                            value={formKey}
                            onChange={(_, d) => setFormKey(d.value.toUpperCase())}
                            placeholder="e.g. MY_COMPANY"
                            disabled={!!drawer.editing}
                        />
                    </Field>
                    <Field label="Default Value">
                        <Input
                            id={"input-personal-var-value"}
                            value={formValue}
                            onChange={(_, d) => setFormValue(d.value)}
                            placeholder="e.g. Smith & Co"
                        />
                    </Field>
                    {formError && (
                        <Text className={styles.errorText}>{formError}</Text>
                    )}
                    <div className={styles.buttonRow}>
                        <Button
                            id={"button-personal-var-cancel"}
                            appearance="secondary"
                            shape={"circular"}
                            onClick={() => setDrawer({open: false})}
                        >
                            Cancel
                        </Button>
                        <Button
                            id={"button-personal-var-save"}
                            appearance="primary"
                            shape={"circular"}
                            onClick={handleSave}
                            disabled={saving}
                        >
                            {saving ? 'Saving…' : 'Save'}
                        </Button>
                    </div>
                </DrawerBody>
            </Drawer>
        </>
    );
});

export default PersonalVariablesTab;
