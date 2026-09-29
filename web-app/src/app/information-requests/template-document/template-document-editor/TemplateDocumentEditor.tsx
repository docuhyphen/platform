import React from "react";
import {Tab, TabList} from "@fluentui/react-components";
import {TemplateDocumentContext} from "../TemplateDocumentContext.ts";
import {TemplateDocumentEditor as TemplateDocumentEditorState, TemplateDocumentPanel} from "../useTemplateDocumentEditor.ts";
import TemplateSectionsPanel from "../template-sections-panel/TemplateSectionsPanel.tsx";
import TemplateGroupsPanel from "../template-groups-panel/TemplateGroupsPanel.tsx";
import TemplateConditionsPanel from "../template-conditions-panel/TemplateConditionsPanel.tsx";
import TemplateReviewPanel from "../template-review-panel/TemplateReviewPanel.tsx";
import TemplateSettingsPanel from "../template-settings-panel/TemplateSettingsPanel.tsx";
import {useTemplateDocumentEditorStyles} from "./TemplateDocumentEditorStyles.tsx";

interface TemplateDocumentEditorProps
{
    editor: TemplateDocumentEditorState;
    extraPanel?: {label: string; content: React.ReactNode};
}

const PANELS: {value: TemplateDocumentPanel; label: string}[] = [
    {value: "sections", label: "Sections"},
    {value: "groups", label: "Groups"},
    {value: "conditions", label: "Conditions"},
    {value: "review", label: "Review"},
    {value: "settings", label: "Settings"},
];

const TemplateDocumentEditor = ({editor, extraPanel}: TemplateDocumentEditorProps) =>
{
    const styles = useTemplateDocumentEditorStyles();

    return (
        <TemplateDocumentContext.Provider value={editor}>
            <div id={"template-document-editor"}
                 className={styles.editor}>
                <TabList id={"template-document-tabs"}
                         selectedValue={editor.panel}
                         onTabSelect={(_, data) => editor.setPanel(data.value as TemplateDocumentPanel)}
                         className={styles.tabs}>
                    {PANELS.map(panel => (
                        <Tab key={panel.value}
                             id={`template-document-tab-${panel.value}`}
                             value={panel.value}>
                            {panel.label}
                        </Tab>
                    ))}
                    {extraPanel && (
                        <Tab id={"template-document-tab-extra"}
                             value={"extra"}>
                            {extraPanel.label}
                        </Tab>
                    )}
                </TabList>
                <div id={"template-document-panel"}
                     className={styles.panel}>
                    {editor.panel === "sections" && <TemplateSectionsPanel/>}
                    {editor.panel === "groups" && <TemplateGroupsPanel/>}
                    {editor.panel === "conditions" && <TemplateConditionsPanel/>}
                    {editor.panel === "review" && <TemplateReviewPanel/>}
                    {editor.panel === "settings" && <TemplateSettingsPanel/>}
                    {editor.panel === "extra" && extraPanel?.content}
                </div>
            </div>
        </TemplateDocumentContext.Provider>
    );
};

export default TemplateDocumentEditor;
