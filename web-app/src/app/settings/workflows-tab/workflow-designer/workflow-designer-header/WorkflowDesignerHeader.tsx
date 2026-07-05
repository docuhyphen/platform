import {Button, Text, Tooltip} from "@fluentui/react-components";
import {BackIcon, InfoIcon} from "../../../../components/IconBundles.tsx";
import {useWorkflowDesignerHeaderStyles} from "./WorkflowDesignerHeaderStyles.tsx";

interface Props
{
    isEdit: boolean;
    workflowName?: string;
    onBack: () => void;
    onHelp: () => void;
}

const WorkflowDesignerHeader = ({isEdit, workflowName, onBack, onHelp}: Props) =>
{
    const styles = useWorkflowDesignerHeaderStyles();

    return (
        <div className={styles.topBar}>
            <Button
                id="workflow-designer-back-btn"
                appearance="subtle"
                icon={<BackIcon/>}
                shape={"circular"}
                onClick={onBack}
            >
                Back
            </Button>
            <div className={styles.titleGroup}>
                <Text size={500}
                      weight="semibold">
                    {isEdit ? "Edit Workflow" : "New Workflow"}
                </Text>
                {workflowName ? (
                    <Text id="workflow-designer-name"
                          size={400}
                          className={styles.workflowName}
                          title={workflowName}>
                        {workflowName}
                    </Text>
                ) : null}
            </div>
            <Tooltip content="Workflow help"
                     relationship="label">
                <Button
                    id="workflow-designer-help-btn"
                    appearance="subtle"
                    shape="circular"
                    size="small"
                    icon={<InfoIcon/>}
                    className={styles.helpButton}
                    onClick={onHelp}
                    aria-label="Open workflow help"
                />
            </Tooltip>
        </div>
    );
};

export default WorkflowDesignerHeader;
