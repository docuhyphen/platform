import {ControlButton, Controls} from "@xyflow/react";
import {
    CollapseIcon,
    ExpandIcon,
    FullScreenEnterIcon,
    FullScreenExitIcon,
    ResetZoomIcon,
} from "../../../../../components/IconBundles.tsx";

interface WorkflowGraphCanvasControlsProps
{
    isEnlarged: boolean;
    isFullscreen: boolean;
    onFitView: () => void;
    onToggleEnlarged: () => void;
    onToggleFullscreen: () => void;
}

export function WorkflowGraphCanvasControls(props: WorkflowGraphCanvasControlsProps)
{
    const {isEnlarged, isFullscreen, onFitView, onToggleEnlarged, onToggleFullscreen} = props;

    return (
        <Controls showFitView={false}
                  showInteractive={false}>
            <ControlButton id="workflow-graph-fit-view-btn"
                           onClick={onFitView}
                           title="Fit to window"
                           aria-label="Fit to window">
                <ResetZoomIcon />
            </ControlButton>
            <ControlButton id="workflow-graph-enlarge-btn"
                           onClick={onToggleEnlarged}
                           title={isEnlarged ? "Exit enlarged view" : "Enlarge"}
                           aria-label={isEnlarged ? "Exit enlarged view" : "Enlarge"}
                           aria-pressed={isEnlarged}>
                {isEnlarged ? <CollapseIcon /> : <ExpandIcon />}
            </ControlButton>
            <ControlButton id="workflow-graph-fullscreen-btn"
                           onClick={onToggleFullscreen}
                           title={isFullscreen ? "Exit full screen" : "Full screen"}
                           aria-label={isFullscreen ? "Exit full screen" : "Full screen"}
                           aria-pressed={isFullscreen}>
                {isFullscreen ? <FullScreenExitIcon /> : <FullScreenEnterIcon />}
            </ControlButton>
        </Controls>
    );
}
