import {Button, Drawer, DrawerBody, DrawerHeader, DrawerHeaderTitle, Text} from '@fluentui/react-components';
import {formatSequencePreview} from '../sequenceForm.ts';
import {SequenceEditor} from '../useSequenceEditor.ts';
import SequenceFormFields from './SequenceFormFields.tsx';
import {useSequenceDrawerStyles} from './SequenceDrawerStyles.tsx';

interface SequenceDrawerProps
{
    editor: SequenceEditor;
}

const SequenceDrawer = ({editor}: SequenceDrawerProps) =>
{
    const styles = useSequenceDrawerStyles();

    return (
        <Drawer
            id={"organization-sequences-tab-drawer-1"}
            open={editor.open}
            onOpenChange={(_, d) => editor.setOpen(d.open)}
            position="end"
            size="small"
        >
            <DrawerHeader id={"organization-sequences-tab-drawer-header-1"}>
                <DrawerHeaderTitle id={"organization-sequences-tab-drawer-header-title-1"}>{editor.editing ? 'Edit Sequence' : 'New Sequence'}</DrawerHeaderTitle>
            </DrawerHeader>
            <DrawerBody id={"organization-sequences-tab-drawer-body-1"}
                        className={styles.drawerBody}>
                <SequenceFormFields form={editor.form}
                                    setForm={editor.setForm}
                                    formError={editor.formError}
                                    isEditing={!!editor.editing}/>
                <div id={"organization-sequences-tab-div-7"}
                     className={styles.previewBox}>
                    <Text
                        id={"organization-sequences-tab-text-10"}
                        size={200}
                        className={styles.previewLabel}
                    >
                        Preview:{' '}
                    </Text>
                    <code id={"organization-sequences-tab-code-4"}>{formatSequencePreview(editor.form, editor.editing?.currentValue ?? 0)}</code>
                </div>
                {editor.formError && (
                    <Text id={"organization-sequences-tab-text-11"}
                          className={styles.formError}>{editor.formError}</Text>
                )}
                <div id={"organization-sequences-tab-div-8"}
                     className={styles.buttonRow}>
                    <Button
                        id={"button-seq-cancel"}
                        appearance="secondary"
                        shape={"circular"}
                        onClick={editor.close}
                    >
                        Cancel
                    </Button>
                    <Button
                        id={"button-seq-save"}
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

export default SequenceDrawer;
