import {Button, Tooltip} from "@fluentui/react-components";
import {VerticalLayoutIcon} from "../../../../../components/IconBundles.tsx";
import {WorkflowGraphDirection} from "../../workflowGraphModels.ts";
import {useWorkflowGraphDirectionToggleStyles} from "./WorkflowGraphDirectionToggleStyles.tsx";

interface WorkflowGraphDirectionToggleProps
{
    direction: WorkflowGraphDirection;
    directionToggleId?: string;
    onDirectionToggle?: (checked: boolean) => void;
}

export function WorkflowGraphDirectionToggle({direction, directionToggleId, onDirectionToggle}: WorkflowGraphDirectionToggleProps)
{
    const styles = useWorkflowGraphDirectionToggleStyles();
    const label = direction === "TB" ? "Display horizontally" : "Display vertically";

    return (
        <div id={directionToggleId ? `${directionToggleId}-container` : "workflow-graph-direction-toggle-container"}
             className={styles.directionToggle}>
            <Tooltip content={label}
                     relationship="label">
                <Button id={directionToggleId ?? "workflow-graph-direction-toggle"}
                        className={styles.directionToggleButton}
                        appearance="secondary"
                        shape={"circular"}
                        icon={<VerticalLayoutIcon className={styles.directionToggleIcon} />}
                        aria-label={label}
                        aria-pressed={direction === "TB"}
                        onClick={() => onDirectionToggle?.(direction !== "TB")} />
            </Tooltip>
        </div>
    );
}
