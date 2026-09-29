import {InformationRequestTemplateDto, SchemaDefinitionDto} from "../../../models/models.tsx";
import TemplateDocumentEditor from "../../../information-requests/template-document/template-document-editor/TemplateDocumentEditor.tsx";
import {useTemplateDocumentEditor} from "../../../information-requests/template-document/useTemplateDocumentEditor.ts";
import TemplateEditorHeader from "../template-editor-header/TemplateEditorHeader.tsx";
import TemplateEditorFooter from "../template-editor-footer/TemplateEditorFooter.tsx";
import TemplateRefusalBar from "../template-refusal-bar/TemplateRefusalBar.tsx";
import TemplateVersionsPanel from "../template-versions-panel/TemplateVersionsPanel.tsx";
import {TemplateEditorCommands} from "./templateEditorTypes.ts";
import {useTemplateEditorLifecycle} from "./useTemplateEditorLifecycle.ts";
import {useTemplateEditorStyles} from "./TemplateEditorStyles.tsx";

interface TemplateEditorProps
{
    template: InformationRequestTemplateDto;
    schemas: SchemaDefinitionDto[];
    canManage: boolean;
    commands: TemplateEditorCommands;
    onClose: () => void;
}

const TemplateEditor = ({template, schemas, canManage, commands, onClose}: TemplateEditorProps) =>
{
    const styles = useTemplateEditorStyles();
    const readOnly = !canManage || !template.draftVersion;
    const source = template.draftVersion ?? template.latestPublishedVersion;
    const editor = useTemplateDocumentEditor(source, `${source?.id ?? "none"}:${template.updatedAt}`, schemas, readOnly);
    const lifecycle = useTemplateEditorLifecycle(editor, commands);

    return (
        <section id={"information-request-template-editor"}
                 aria-label={`Template ${template.displayName}`}
                 className={styles.editor}>
            <TemplateEditorHeader template={template}
                                  readOnly={readOnly}
                                  onBack={onClose}/>
            {lifecycle.refusal && (
                <TemplateRefusalBar refusal={lifecycle.refusal}
                                    onShow={() =>
                                    {
                                        if (lifecycle.refusal?.target) editor.goTo(lifecycle.refusal.target);
                                    }}
                                    onDismiss={lifecycle.dismissRefusal}/>
            )}
            <TemplateDocumentEditor editor={editor}
                                    extraPanel={{
                                        label: "Versions",
                                        content: (
                                            <TemplateVersionsPanel template={template}
                                                                   canManage={canManage}
                                                                   busy={lifecycle.busy}
                                                                   onStartDraft={lifecycle.startDraft}
                                                                   onRetire={lifecycle.retire}
                                                                   onClone={lifecycle.clone}/>
                                        ),
                                    }}/>
            {!readOnly && (
                <TemplateEditorFooter editor={editor}
                                      busy={lifecycle.busy}
                                      onSave={() => void lifecycle.save()}
                                      onPublish={() => void lifecycle.publish()}/>
            )}
        </section>
    );
};

export default TemplateEditor;
