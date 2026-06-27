import {useState} from "react";
import {SelectTabData, SelectTabEvent, Tab, TabList, TabValue} from "@fluentui/react-components";
import {WorkflowDefinitionSummaryDto} from "../../models/models.tsx";
import {useWorkflowsTabStyles} from "./WorkflowsTabStyles.tsx";
import WorkflowsListView from "./workflows-list-view/WorkflowsListView.tsx";
import WorkflowDesigner from "./workflow-designer/WorkflowDesigner.tsx";
import WorkflowInstanceDashboard from "./workflow-instance-dashboard/WorkflowInstanceDashboard.tsx";
import WorkflowInstanceDetail from "./workflow-instance-detail/WorkflowInstanceDetail.tsx";

type SubTab = "workflows" | "activity";

interface DesignerTarget
{
    definitionId?: string;
    scope?: 'PERSONAL' | 'ORG' | 'APP';
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

    const openDesigner = (definition?: WorkflowDefinitionSummaryDto, scope?: 'PERSONAL' | 'ORG' | 'APP') =>
        setDesignerTarget({definitionId: definition?.id, scope});

    const closeDesigner = () => setDesignerTarget(null);

    if (designerTarget !== null)
    {
        return (
            <WorkflowDesigner
                definitionId={designerTarget.definitionId}
                scope={designerTarget.scope}
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
                        onEdit={(def) => openDesigner(def, def.scope as 'PERSONAL' | 'ORG' | 'APP')}
                        onNew={(scope) => openDesigner(undefined, scope)}
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


