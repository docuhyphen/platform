import {useState} from "react";
import {Spinner} from "@fluentui/react-components";
import {
    InformationRequestTemplateDto,
    InformationRequestTemplateScopeKind,
    SchemaDefinitionDto,
} from "../../../models/models.tsx";
import InformationRequestLoadError
    from "../../../information-requests/shared/load-error/InformationRequestLoadError.tsx";
import TemplateDocumentEditor
    from "../../../information-requests/template-document/template-document-editor/TemplateDocumentEditor.tsx";
import {useTemplateDocumentEditor} from "../../../information-requests/template-document/useTemplateDocumentEditor.ts";
import SettingsLeaveDialog from "../../settings-leave-guard/SettingsLeaveDialog.tsx";
import {useUnsavedSettingsWork} from "../../settings-leave-guard/settingsLeaveGuard.ts";
import TemplateEditorHeader from "../template-editor-header/TemplateEditorHeader.tsx";
import TemplateEditorFooter from "../template-editor-footer/TemplateEditorFooter.tsx";
import TemplateRefusalBar from "../template-refusal-bar/TemplateRefusalBar.tsx";
import TemplateVersionsPanel from "../template-versions-panel/TemplateVersionsPanel.tsx";
import {TemplateEditorCommands} from "./templateEditorTypes.ts";
import {currentVersionNumberOf, useInspectedTemplateVersion} from "./useInspectedTemplateVersion.ts";
import {useTemplateEditorLifecycle} from "./useTemplateEditorLifecycle.ts";
import {useTemplateEditorStyles} from "./TemplateEditorStyles.tsx";

interface TemplateEditorProps
{
    template: InformationRequestTemplateDto;
    schemas: SchemaDefinitionDto[];
    schemasError: string | null;
    onRetrySchemas: () => void;
    canManage: boolean;
    copyTargets: InformationRequestTemplateScopeKind[];
    commands: TemplateEditorCommands;
    onClose: () => void;
}

const TemplateEditor = ({
                            template,
                            schemas,
                            schemasError,
                            onRetrySchemas,
                            canManage,
                            copyTargets,
                            commands,
                            onClose,
                        }: TemplateEditorProps) =>
{
    const styles = useTemplateEditorStyles();
    const inspected = useInspectedTemplateVersion(template);
    const inspecting = inspected.versionNumber !== null;
    const readOnly = !canManage || !template.draftVersion || inspecting;
    const source = inspecting ? inspected.version ?? undefined : template.draftVersion ?? template.latestPublishedVersion ?? undefined;
    const sourceKey = inspecting
        ? `inspect:${template.id}:${inspected.versionNumber}:${inspected.version?.id ?? "loading"}`
        : `${source?.id ?? "none"}:${template.updatedAt}:${template.draftETag ?? ""}`;
    const editor = useTemplateDocumentEditor(source, sourceKey, schemas, readOnly);
    const lifecycle = useTemplateEditorLifecycle(editor, commands);
    const unsaved = editor.dirty && !readOnly;
    const [leaving, setLeaving] = useState(false);
    useUnsavedSettingsWork(unsaved);

    return (
        <section id={"information-request-template-editor"}
                 aria-label={`Template ${template.displayName}`}
                 className={styles.editor}>
            <TemplateEditorHeader template={template}
                                  readOnly={readOnly}
                                  shown={inspected.standing}
                                  onShowCurrent={inspecting && inspected.hasCurrent ? inspected.showCurrent : undefined}
                                  onBack={() => unsaved ? setLeaving(true) : onClose()}/>
            {schemasError && (
                <InformationRequestLoadError id={"information-request-template-schemas-load-error"}
                                             message={schemasError}
                                             onRetry={onRetrySchemas}/>
            )}
            {canManage && editor.fieldBindingsError && (
                <InformationRequestLoadError id={"information-request-template-schema-version-load-error"}
                                             message={editor.fieldBindingsError}
                                             onRetry={editor.reloadFieldBindings}/>
            )}
            {inspected.error && (
                <InformationRequestLoadError id={"information-request-template-version-load-error"}
                                             message={inspected.error}
                                             onRetry={() => void inspected.reload()}/>
            )}
            {lifecycle.refusal && (
                <TemplateRefusalBar refusal={lifecycle.refusal}
                                    onShow={() =>
                                    {
                                        if (lifecycle.refusal?.target) editor.goTo(lifecycle.refusal.target);
                                    }}
                                    onKeepChanges={() => void lifecycle.keepChanges()}
                                    onOpenLatest={() => void lifecycle.openLatest()}
                                    onDismiss={lifecycle.dismissRefusal}/>
            )}
            {inspecting && !inspected.version && !inspected.error && (
                <Spinner id={"information-request-template-version-loading"}
                         size={"small"}
                         label={`Loading version ${inspected.versionNumber}`}/>
            )}
            {(!inspecting || inspected.version) && (
                <TemplateDocumentEditor editor={editor}
                                        extraPanel={{
                                            label: "Versions",
                                            content: (
                                                <TemplateVersionsPanel template={template}
                                                                       canManage={canManage}
                                                                       copyTargets={copyTargets}
                                                                       busy={lifecycle.busy}
                                                                       shownVersionNumber={inspected.versionNumber ?? currentVersionNumberOf(template)}
                                                                       onView={inspected.view}
                                                                       onStartDraft={lifecycle.startDraft}
                                                                       onRetire={lifecycle.retire}
                                                                       onClone={lifecycle.clone}/>
                                            ),
                                        }}/>
            )}
            {!readOnly && (
                <TemplateEditorFooter editor={editor}
                                      busy={lifecycle.busy}
                                      onSave={() => void lifecycle.save()}
                                      onPublish={() => void lifecycle.publish()}/>
            )}
            <SettingsLeaveDialog open={leaving}
                                 title={"Discard unsaved changes?"}
                                 onLeave={onClose}
                                 onStay={() => setLeaving(false)}/>
        </section>
    );
};

export default TemplateEditor;
