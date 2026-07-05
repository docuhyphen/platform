import {Button} from "@fluentui/react-components";
import {useWorkflowDesignerActionBarStyles} from "./WorkflowDesignerActionBarStyles.tsx";

interface Props
{
    onCancel: () => void;
    onSave: () => void;
}

const WorkflowDesignerActionBar = ({onCancel, onSave}: Props) =>
{
    const styles = useWorkflowDesignerActionBarStyles();

    return (
        <div className={styles.saveBar}>
            <Button
                id="workflow-designer-cancel-btn"
                appearance="secondary"
                shape={"circular"}
                onClick={onCancel}
            >
                Cancel
            </Button>
            <Button
                id="workflow-designer-save-btn"
                appearance="primary"
                shape={"circular"}
                onClick={onSave}
            >
                Save Workflow
            </Button>
        </div>
    );
};

export default WorkflowDesignerActionBar;
