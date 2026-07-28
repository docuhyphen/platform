import {useState} from "react";
import {
    Tab,
    TabList,
    TabValue,
} from "@fluentui/react-components";
import {WorkflowDefinitionSummaryDto} from "../../models/models.tsx";
import WorkflowDesigner from "../../settings/workflows-tab/workflow-designer/WorkflowDesigner.tsx";
import PlatformBlueprints from "./PlatformBlueprints.tsx";
import PlatformCommunications from "./PlatformCommunications.tsx";
import PlatformDocuments from "./PlatformDocuments.tsx";
import PlatformFields from "./PlatformFields.tsx";
import PlatformWorkflowTemplates from "./PlatformWorkflowTemplates.tsx";
import {usePlatformContentStyles} from "./PlatformContentStyles.tsx";

const PlatformContent = () =>
{
    const styles = usePlatformContentStyles();
    const [selectedContent, setSelectedContent] = useState<TabValue>("workflows");
    const [editing, setEditing] = useState<WorkflowDefinitionSummaryDto | "new" | null>(null);

    if (editing)
    {
        return (
            <WorkflowDesigner
                definitionId={editing === "new" ? undefined : editing.id}
                scope={"APP"}
                enforcedScope={"APP"}
                createAsTemplate={true}
                backDestinationLabel={"Platform Content"}
                onBack={() => setEditing(null)}
                onSaved={() => setEditing(null)}/>
        );
    }

    return (
        <div
            id={"platform-content"}
            className={styles.container}>
            <div
                id={"platform-content-navigation"}
                className={styles.stickyBlock}>
                <TabList
                    id={"platform-content-tabs"}
                    selectedValue={selectedContent}
                    onTabSelect={(_, data) => setSelectedContent(data.value)}>
                    <Tab
                        id={"platform-content-workflows-tab"}
                        value={"workflows"}>
                        Workflows
                    </Tab>
                    <Tab
                        id={"platform-content-blueprints-tab"}
                        value={"blueprints"}>
                        Blueprints
                    </Tab>
                    <Tab
                        id={"platform-content-communications-tab"}
                        value={"communications"}>
                        Communications
                    </Tab>
                    <Tab
                        id={"platform-content-documents-tab"}
                        value={"documents"}>
                        Documents
                    </Tab>
                    <Tab
                        id={"platform-content-fields-tab"}
                        value={"fields"}>
                        Fields
                    </Tab>
                </TabList>
            </div>
            <div
                id={"platform-content-selected-tab"}
                className={styles.tabContent}>
                {selectedContent === "workflows" && (
                    <PlatformWorkflowTemplates
                        onCreate={() => setEditing("new")}
                        onEdit={setEditing}/>
                )}
                {selectedContent === "blueprints" && <PlatformBlueprints/>}
                {selectedContent === "communications" && <PlatformCommunications/>}
                {selectedContent === "documents" && <PlatformDocuments/>}
                {selectedContent === "fields" && <PlatformFields/>}
            </div>
        </div>
    );
};

export default PlatformContent;
