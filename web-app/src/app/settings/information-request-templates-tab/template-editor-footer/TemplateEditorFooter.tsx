import {Button, Text} from "@fluentui/react-components";
import {PublishIcon} from "../../../components/IconBundles.tsx";
import TemplatePublishCheck from "../../../information-requests/template-document/template-publish-check/TemplatePublishCheck.tsx";
import {TemplateDocumentEditor} from "../../../information-requests/template-document/useTemplateDocumentEditor.ts";
import {useTemplateEditorFooterStyles} from "./TemplateEditorFooterStyles.tsx";

interface TemplateEditorFooterProps
{
    editor: TemplateDocumentEditor;
    busy: boolean;
    onSave: () => void;
    onPublish: () => void;
}

const TemplateEditorFooter = ({editor, busy, onSave, onPublish}: TemplateEditorFooterProps) =>
{
    const styles = useTemplateEditorFooterStyles();

    return (
        <footer id={"information-request-template-editor-footer"}
                className={styles.footer}>
            <TemplatePublishCheck id={"information-request-template-publish-check"}
                                  title={"Before publishing"}
                                  problems={editor.problems}
                                  readyText={"Nothing found that would stop this draft from being published."}
                                  onShow={editor.goTo}/>
            <div id={"information-request-template-editor-actions"}
                 className={styles.actions}>
                <Text id={"information-request-template-save-state"}
                      aria-live={"polite"}
                      className={styles.state}>
                    {busy ? "Working..." : editor.dirty ? "Unsaved changes" : "All changes saved"}
                </Text>
                <Button id={"information-request-template-save-draft"}
                        appearance={"primary"}
                        shape={"circular"}
                        disabled={busy || !editor.dirty}
                        onClick={onSave}>
                    Save draft
                </Button>
                <Button id={"information-request-template-publish-draft"}
                        appearance={"secondary"}
                        shape={"circular"}
                        icon={<PublishIcon/>}
                        disabled={busy || editor.problems.length > 0}
                        onClick={onPublish}>
                    Publish
                </Button>
            </div>
        </footer>
    );
};

export default TemplateEditorFooter;
