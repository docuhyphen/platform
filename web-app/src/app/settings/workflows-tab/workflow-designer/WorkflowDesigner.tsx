import {useEffect, useState} from "react";
import {MessageBar, MessageBarBody, Spinner} from "@fluentui/react-components";
import {useWorkflowDesignerStyles} from "./WorkflowDesignerStyles.tsx";
import {useHelpSidebar} from "../../../../context/HelpSidebarContext.tsx";
import SaveWorkflowDialog from "../save-workflow-dialog/SaveWorkflowDialog.tsx";
import WorkflowDesignerHeader from "./workflow-designer-header/WorkflowDesignerHeader.tsx";
import WorkflowDesignerActionBar from "./workflow-designer-action-bar/WorkflowDesignerActionBar.tsx";
import WorkflowDesignerFormPages from "./workflow-designer-form-pages/WorkflowDesignerFormPages.tsx";
import WorkflowDiagramPanel from "./workflow-diagram-panel/WorkflowDiagramPanel.tsx";
import WorkflowDiscardDialog from "./workflow-discard-dialog/WorkflowDiscardDialog.tsx";
import WorkflowFormColumn from "./workflow-form-column/WorkflowFormColumn.tsx";
import WorkflowScopeViolation from "./workflow-scope-violation/WorkflowScopeViolation.tsx";
import {useFormPageNavigation} from "./useFormPageNavigation.ts";
import {useWorkflowDefinitionEditor} from "./useWorkflowDefinitionEditor.ts";
import {WorkflowScope} from "./workflowDesignerDefaults.ts";

interface Props
{
    definitionId?: string;
    scope?: WorkflowScope;
    backDestinationLabel: string;
    onBack: () => void;
    onSaved: () => void;
    enforcedScope?: 'APP';
    createAsTemplate?: boolean;
}

const WorkflowDesigner = ({
                              definitionId,
                              scope,
                              backDestinationLabel,
                              onBack,
                              onSaved,
                              enforcedScope,
                              createAsTemplate = false,
                          }: Props) =>
{
    const styles = useWorkflowDesignerStyles();
    const {openHelpArticle} = useHelpSidebar();
    const [showDiscardDialog, setShowDiscardDialog] = useState(false);
    const [showSaveDialog, setShowSaveDialog] = useState(false);
    const editor = useWorkflowDefinitionEditor({definitionId, scope, enforcedScope, createAsTemplate, onSaved});
    const {formPage, transitionDirection, navigate, reset: resetFormPage} = useFormPageNavigation();
    const {load} = editor;

    useEffect(() =>
    {
        resetFormPage();
        load();
    }, [load, resetFormPage]);

    const requestSave = () =>
    {
        if (editor.validateForSave()) setShowSaveDialog(true);
    };

    const handleBack = () => editor.isDirty() ? setShowDiscardDialog(true) : onBack();

    if (editor.loading) return <Spinner id={"workflow-designer-loading-workflow-spinner"}
                                        label="Loading workflow..."
                                        size="small"/>;
    if (editor.scopeViolation)
    {
        return <WorkflowScopeViolation message={editor.error}
                                       onBack={onBack}/>;
    }

    return (
        <div id={"workflow-designer-container"}
             className={styles.container}>
            <div id={"workflow-designer-sticky-toolbar"}
                 className={styles.stickyToolbar}>
                <WorkflowDesignerHeader
                    isEdit={!!definitionId}
                    workflowName={editor.state.name}
                    backDestinationLabel={backDestinationLabel}
                    onBack={handleBack}
                    onHelp={() => openHelpArticle("building-a-workflow")}
                />
            </div>

            <div id={"workflow-designer-split-content-shell"}
                 className={styles.splitContentShell}>
                {editor.error && (
                    <MessageBar id={"workflow-designer-split-content-shell-message-bar"}
                                intent="error">
                        <MessageBarBody id={"workflow-designer-error-message-bar-body-2"}>{editor.error}</MessageBarBody>
                    </MessageBar>
                )}

                <div id={"workflow-designer-split-content"}
                     className={styles.splitContent}>
                    <WorkflowDiagramPanel state={editor.state}/>
                    <WorkflowFormColumn formPage={formPage}
                                        transitionDirection={transitionDirection}>
                        <WorkflowDesignerFormPages
                            formPage={formPage}
                            state={editor.state}
                            onPatch={editor.patch}
                            triggers={editor.triggers}
                            triggersError={editor.triggersError}
                            isEdit={!!definitionId}
                            subjectFields={editor.subjectFields}
                            defScope={editor.defScope}
                            triggerKind={editor.triggerKind}
                            platformMode={enforcedScope === "APP"}
                            onAddStep={editor.addStep}
                            onUpdateStep={editor.updateStep}
                            onRemoveStep={editor.removeStep}
                            onNavigate={navigate}
                        />
                    </WorkflowFormColumn>
                </div>
            </div>

            <WorkflowDesignerActionBar
                onCancel={handleBack}
                onSave={requestSave}
            />

            <SaveWorkflowDialog
                open={showSaveDialog}
                onClose={() => setShowSaveDialog(false)}
                onConfirm={editor.performSave}
                isEdit={!!definitionId}
                state={editor.state}
                triggers={editor.triggers}
                warnings={editor.graphWarnings}
            />

            <WorkflowDiscardDialog
                open={showDiscardDialog}
                onOpenChange={setShowDiscardDialog}
                onDiscard={onBack}
                onKeepEditing={() => setShowDiscardDialog(false)}
            />
        </div>
    );
};

export default WorkflowDesigner;
