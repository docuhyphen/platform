import {Button, Text, Tooltip} from "@fluentui/react-components";
import {BackIcon, InfoIcon} from "../../../../components/IconBundles.tsx";
import {useWorkflowDesignerHeaderStyles} from "./WorkflowDesignerHeaderStyles.tsx";

interface Props
{
    isEdit: boolean;
    workflowName?: string;
    backDestinationLabel: string;
    onBack: () => void;
    onHelp: () => void;
}

const WorkflowDesignerHeader = ({isEdit, workflowName, backDestinationLabel, onBack, onHelp}: Props) =>
{
    const styles = useWorkflowDesignerHeaderStyles();

    return (
        <div id={"workflow-designer-header-div-1"}
             className={styles.topBar}>
            <div id={"workflow-designer-header-div-2"}
                 className={styles.topBarLeft}>
                <span id={"workflow-designer-header-span-1"}
                      className={styles.backNavButton}>
                    <Button
                        id="workflow-designer-back-btn"
                        appearance="subtle"
                        icon={<BackIcon/>}
                        shape={"circular"}
                        size={"small"}
                        onClick={onBack}>
                    Back to {backDestinationLabel}
                    </Button>
                </span>
                <Text id={"workflow-designer-header-text-1"}
                      size={500}
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
