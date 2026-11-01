import {lazy, Suspense} from "react";
import {Spinner} from "@fluentui/react-components";
import {WorkflowDesignerState} from "../../../../models/models.tsx";
import {useWorkflowDiagramPanelStyles} from "./WorkflowDiagramPanelStyles.tsx";

const WorkflowDefinitionPreview = lazy(
    () => import("../workflow-definition-preview/WorkflowDefinitionPreview.tsx"),
);

interface Props
{
    state: WorkflowDesignerState;
}

const WorkflowDiagramPanel = ({state}: Props) =>
{
    const styles = useWorkflowDiagramPanelStyles();

    return (
        <div id={"workflow-designer-split-diagram-column"}
             className={styles.splitDiagramColumn}>
            <div id={"workflow-designer-diagram-panel"}
                 className={styles.diagramPanel}>
                <Suspense
                    fallback={
                        <div id={"workflow-designer-preview-loading"}
                             className={styles.previewLoading}>
                            <Spinner
                                id="workflow-designer-preview-spinner"
                                label="Loading preview"
                            />
                        </div>
                    }
                >
                    <WorkflowDefinitionPreview
                        state={state}
                        defaultDirection="TB"
                        fillHeight={true}
                    />
                </Suspense>
            </div>
        </div>
    );
};

export default WorkflowDiagramPanel;
