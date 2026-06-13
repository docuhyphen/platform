import {useState} from "react";
import {SelectTabData, SelectTabEvent, Tab, TabList, TabValue} from "@fluentui/react-components";
import {WorkflowDefinitionSummaryDto} from "../../models/models.tsx";
import {useWorkflowsTabStyles} from "./WorkflowsTabStyles.tsx";
import WorkflowsListView from "./WorkflowsListView.tsx";
import WorkflowDesigner from "./WorkflowDesigner.tsx";
import WorkflowInstanceDashboard from "./WorkflowInstanceDashboard.tsx";
import WorkflowInstanceDetail from "./WorkflowInstanceDetail.tsx";

type SubTab = "workflows" | "activity";

interface DesignerTarget
{
    definitionId?: string;
}

const WorkflowsTab = () =>
{
    const styles = useWorkflowsTabStyles();
    const [subTab, setSubTab] = useState<TabValue>("workflows" satisfies SubTab);
    const [designerTarget, setDesignerTarget] = useState<DesignerTarget | null>(null);
    const [selectedInstanceId, setSelectedInstanceId] = useState<string | null>(null);

    const onTabSelect = (_: SelectTabEvent, data: SelectTabData) =>
    {
        setSubTab(data.value);
        setDesignerTarget(null);
    };

    const openDesigner = (definition?: WorkflowDefinitionSummaryDto) =>
        setDesignerTarget({definitionId: definition?.id});

    const closeDesigner = () => setDesignerTarget(null);

    if (designerTarget !== null)
    {
        return (
            <WorkflowDesigner
                definitionId={designerTarget.definitionId}
                onBack={closeDesigner}
                onSaved={closeDesigner}
            />
        );
    }

    return (
        <div className={styles.container}>
            <div className={styles.tabListWrapper}>
                <TabList selectedValue={subTab} onTabSelect={onTabSelect} size="medium">
                    <Tab value={"workflows" satisfies SubTab}>Workflows</Tab>
                    <Tab value={"activity" satisfies SubTab}>Activity</Tab>
                </TabList>
            </div>

            <div className={styles.content}>
                {subTab === "workflows" && (
                    <WorkflowsListView
                        onEdit={openDesigner}
                        onNew={() => openDesigner()}
                    />
                )}

                {subTab === "activity" && (
                    <>
                        <WorkflowInstanceDashboard onSelectInstance={setSelectedInstanceId}/>
                        <WorkflowInstanceDetail
                            instanceId={selectedInstanceId}
                            onDismiss={() => setSelectedInstanceId(null)}
                        />
                    </>
                )}
            </div>
        </div>
    );
};

export default WorkflowsTab;


