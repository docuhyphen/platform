import {MessageBar, MessageBarBody, MessageBarTitle} from "@fluentui/react-components";
import {useWorkflowGraphCanvasNoticesStyles} from "./WorkflowGraphCanvasNoticesStyles.tsx";

interface WorkflowGraphCanvasNoticesProps
{
    warnings: string[];
    isEmpty: boolean;
    emptyMessage: string;
    isLarge: boolean;
}

export function WorkflowGraphCanvasNotices({warnings, isEmpty, emptyMessage, isLarge}: WorkflowGraphCanvasNoticesProps)
{
    const styles = useWorkflowGraphCanvasNoticesStyles();

    return (
        <>
            {warnings.length > 0 ? (
                <MessageBar id="workflow-graph-warnings"
                            intent="warning">
                    <MessageBarBody id={"workflow-graph-canvas-message-bar-body-1"}>
                        <MessageBarTitle id={"workflow-graph-canvas-message-bar-title-1"}>Diagram notices</MessageBarTitle>
                        <ul id={"workflow-graph-canvas-ul-1"}
                            className={styles.warningList}>
                            {warnings.map((warning, index) => (
                                <li id={`workflow-graph-canvas-li-1-${index}`}
                                    key={index}>{warning}</li>
                            ))}
                        </ul>
                    </MessageBarBody>
                </MessageBar>
            ) : null}

            {isEmpty ? (
                <MessageBar id="workflow-graph-empty"
                            intent="info">
                    <MessageBarBody id={"workflow-graph-canvas-message-bar-body-2"}>{emptyMessage}</MessageBarBody>
                </MessageBar>
            ) : null}

            {isLarge ? (
                <MessageBar id="workflow-graph-large"
                            intent="info">
                    <MessageBarBody id={"workflow-graph-canvas-message-bar-body-3"}>
                        This workflow is large. The Timeline view may be easier to follow.
                    </MessageBarBody>
                </MessageBar>
            ) : null}
        </>
    );
}
