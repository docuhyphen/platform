import {Component, ErrorInfo, ReactNode} from "react";
import {
    Button,
    MessageBar,
    MessageBarActions,
    MessageBarBody,
    MessageBarTitle,
} from "@fluentui/react-components";
import {TimelineIcon} from "../../../components/IconBundles.tsx";
import {useWorkflowGraphErrorBoundaryStyles} from "./WorkflowGraphErrorBoundaryStyles.tsx";

interface WorkflowGraphErrorBoundaryProps
{
    children: ReactNode;
    onViewTimeline?: () => void;
}

interface WorkflowGraphErrorBoundaryState
{
    hasError: boolean;
}

function WorkflowGraphErrorFallback({onViewTimeline}: { onViewTimeline?: () => void })
{
    const styles = useWorkflowGraphErrorBoundaryStyles();
    return (
        <div id="workflow-graph-error-fallback"
             className={styles.fallback}>
            <MessageBar id="workflow-graph-error-messagebar"
                        intent="error">
                <MessageBarBody>
                    <MessageBarTitle>Failed to load workflow diagram.</MessageBarTitle>
                    The timeline view remains available.
                </MessageBarBody>
                {onViewTimeline ? (
                    <MessageBarActions>
                        <Button id="workflow-graph-error-view-timeline-button"
                                appearance="primary"
                                shape="circular"
                                icon={<TimelineIcon />}
                                onClick={onViewTimeline}>
                            View timeline
                        </Button>
                    </MessageBarActions>
                ) : null}
            </MessageBar>
        </div>
    );
}

/**
 * Isolates a graph rendering failure so it cannot break sibling workflow
 * sections, decision controls, or the Timeline (Decision 16). Directs the user
 * to the Timeline view when a switch callback is provided.
 */
export class WorkflowGraphErrorBoundary
    extends Component<WorkflowGraphErrorBoundaryProps, WorkflowGraphErrorBoundaryState>
{
    constructor(props: WorkflowGraphErrorBoundaryProps)
    {
        super(props);
        this.state = { hasError: false };
    }

    static getDerivedStateFromError(): WorkflowGraphErrorBoundaryState
    {
        return { hasError: true };
    }

    componentDidCatch(error: Error, info: ErrorInfo): void
    {
        console.error("Workflow graph rendering failed", error, info);
    }

    render(): ReactNode
    {
        if (this.state.hasError)
        {
            return <WorkflowGraphErrorFallback onViewTimeline={this.props.onViewTimeline} />;
        }
        return this.props.children;
    }
}
