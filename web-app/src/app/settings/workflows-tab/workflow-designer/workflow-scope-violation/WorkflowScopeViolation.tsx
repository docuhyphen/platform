import {Button, MessageBar, MessageBarBody} from "@fluentui/react-components";
import {useWorkflowScopeViolationStyles} from "./WorkflowScopeViolationStyles.tsx";

interface Props
{
    message: string | null;
    onBack: () => void;
}

const WorkflowScopeViolation = ({message, onBack}: Props) =>
{
    const styles = useWorkflowScopeViolationStyles();

    return (
        <div
            id={"platform-workflow-scope-violation"}
            className={styles.container}>
            <MessageBar id={"workflow-designer-container-message-bar"}
                        intent={"error"}>
                <MessageBarBody id={"workflow-designer-error-message-bar-body"}>{message}</MessageBarBody>
            </MessageBar>
            <Button
                id={"platform-workflow-scope-violation-back"}
                appearance={"secondary"}
                shape={"circular"}
                onClick={onBack}>
                Back to Platform Content
            </Button>
        </div>
    );
};

export default WorkflowScopeViolation;
