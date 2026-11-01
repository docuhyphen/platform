import {Button, Drawer, DrawerBody, DrawerHeader, DrawerHeaderTitle, Field, Input, Text} from '@fluentui/react-components';
import {VariableIdScheme} from '../variableIds.ts';
import {VariableEditor} from '../useVariableEditor.ts';
import {useVariableDrawerStyles} from './VariableDrawerStyles.tsx';

interface VariableDrawerProps
{
    ids: VariableIdScheme;
    editor: VariableEditor;
    createTitle: string;
    keyPlaceholder: string;
    valuePlaceholder: string;
}

const VariableDrawer = ({ids, editor, createTitle, keyPlaceholder, valuePlaceholder}: VariableDrawerProps) =>
{
    const styles = useVariableDrawerStyles();

    return (
        <Drawer
            id={`${ids.tab}-drawer-1`}
            open={editor.open}
            onOpenChange={(_, d) => editor.setOpen(d.open)}
            position="end"
            size="small"
        >
            <DrawerHeader id={`${ids.tab}-drawer-header-1`}>
                <DrawerHeaderTitle id={`${ids.tab}-drawer-header-title-1`}>{editor.editing ? 'Edit Variable' : createTitle}</DrawerHeaderTitle>
            </DrawerHeader>
            <DrawerBody id={`${ids.tab}-drawer-body-1`}
                        className={styles.drawerBody}>
                <Field
                    id={`${ids.tab}-field-1`}
                    label="Key"
                    required
                    hint="Uppercase alphanumeric. Used as {{KEY}} in templates."
                >
                    <Input
                        id={`${ids.input}-key`}
                        value={editor.formKey}
                        onChange={(_, d) => editor.setFormKey(d.value.toUpperCase())}
                        placeholder={keyPlaceholder}
                        disabled={!!editor.editing}
                    />
                </Field>
                <Field id={`${ids.tab}-field-2`}
                       label="Default Value">
                    <Input
                        id={`${ids.input}-value`}
                        value={editor.formValue}
                        onChange={(_, d) => editor.setFormValue(d.value)}
                        placeholder={valuePlaceholder}
                    />
                </Field>
                {editor.formError && (
                    <Text id={`${ids.tab}-text-7`}
                          className={styles.errorText}>{editor.formError}</Text>
                )}
                <div id={`${ids.tab}-div-6`}
                     className={styles.buttonRow}>
                    <Button
                        id={`${ids.button}-cancel`}
                        appearance="secondary"
                        shape={"circular"}
                        onClick={editor.close}
                    >
                        Cancel
                    </Button>
                    <Button
                        id={`${ids.button}-save`}
                        appearance="primary"
                        shape={"circular"}
                        onClick={editor.save}
                        disabled={editor.saving}
                    >
                        {editor.saving ? 'Saving…' : 'Save'}
                    </Button>
                </div>
            </DrawerBody>
        </Drawer>
    );
};

export default VariableDrawer;
