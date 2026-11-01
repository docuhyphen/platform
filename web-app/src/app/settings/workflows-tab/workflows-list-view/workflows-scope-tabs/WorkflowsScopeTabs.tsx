import {Button, Tab, TabList} from "@fluentui/react-components";
import {AddIcon} from "../../../../components/IconBundles.tsx";
import {WORKFLOW_TAB_LABEL} from "../workflowListFilters.ts";
import {WorkflowListTab} from "../workflowListTypes.ts";
import {useWorkflowsScopeTabsStyles} from "./WorkflowsScopeTabsStyles.tsx";

interface Props
{
    activeTab: WorkflowListTab;
    hasOrg: boolean;
    onSelectTab: (tab: WorkflowListTab) => void;
    onNew: (scope: 'PERSONAL' | 'ORG') => void;
}

const WorkflowsScopeTabs = ({activeTab, hasOrg, onSelectTab, onNew}: Props) =>
{
    const styles = useWorkflowsScopeTabsStyles();

    return (
        <div id={"workflows-list-view-div-8"}
             className={styles.outerWrapper}>
            <TabList
                id={"workflows-list-view-tab-list-1"}
                selectedValue={activeTab}
                onTabSelect={(_, d) => onSelectTab(d.value as WorkflowListTab)}
            >
                <Tab id={"workflows-list-view-tab-1"}
                     value="PERSONAL">{WORKFLOW_TAB_LABEL.PERSONAL}</Tab>
                {hasOrg && <Tab id={"workflows-list-view-tab-2"}
                                value="ORG">{WORKFLOW_TAB_LABEL.ORG}</Tab>}
                <Tab id={"workflows-list-view-tab-3"}
                     value="APP">{WORKFLOW_TAB_LABEL.APP}</Tab>
            </TabList>

            {activeTab !== 'APP' && (
                <Button
                    id="workflows-list-create-btn"
                    appearance="subtle"
                    icon={<AddIcon/>}
                    onClick={() => onNew(activeTab)}
                    shape="circular"
                >
                    Create Workflow
                </Button>
            )}
        </div>
    );
};

export default WorkflowsScopeTabs;
