import {SelectTabEvent, SelectTabData, Tab, TabList} from "@fluentui/react-components";
import {useWorkflowViewSwitchStyles} from "./WorkflowViewSwitchStyles.tsx";

export type WorkflowDesignerView = "form" | "preview";

interface Props
{
    view: WorkflowDesignerView;
    onChange: (view: WorkflowDesignerView) => void;
}

const WorkflowViewSwitch = ({view, onChange}: Props) =>
{
    const styles = useWorkflowViewSwitchStyles();

    const onTabSelect = (_: SelectTabEvent, data: SelectTabData) =>
        onChange(data.value as WorkflowDesignerView);

    return (
        <div className={styles.switchBar}>
            <TabList
                id="workflow-designer-view-switch"
                selectedValue={view}
                onTabSelect={onTabSelect}
            >
                <Tab id="workflow-designer-view-form-tab"
                     value="form">
                    Form
                </Tab>
                <Tab id="workflow-designer-view-preview-tab"
                     value="preview">
                    Preview
                </Tab>
            </TabList>
        </div>
    );
};

export default WorkflowViewSwitch;
