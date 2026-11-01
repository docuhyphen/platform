import {ProgressBar, mergeClasses} from "@fluentui/react-components";
import type {HowItWorksStep} from "../howItWorksSteps.ts";
import {HowItWorksPauseButton} from "./HowItWorksPauseButton.tsx";
import {HowItWorksStepMarker} from "./HowItWorksStepMarker.tsx";
import {useHowItWorksStepTimelineStyles} from "./HowItWorksStepTimelineStyles.tsx";

interface HowItWorksStepTimelineProps
{
    step: HowItWorksStep;
    isLast: boolean;
    isActive: boolean;
    isPaused: boolean;
    isVisible: boolean;
    progress: number;
    onTogglePause: () => void;
}

export function HowItWorksStepTimeline({
    step,
    isLast,
    isActive,
    isPaused,
    isVisible,
    progress,
    onTogglePause,
}: HowItWorksStepTimelineProps)
{
    const styles = useHowItWorksStepTimelineStyles();

    return (
        <div id={`how-it-works-timeline-${step.number}`}
             className={styles.stepTimeline}>
            <HowItWorksStepMarker id={`how-it-works-marker-${step.number}`}
                                  number={step.number}
                                  isActive={isActive}
                                  isLast={isLast}/>
            <ProgressBar id={`how-it-works-progress-${step.number}`}
                         className={mergeClasses(
                             styles.stepProgressBar,
                             isLast && styles.lastStepProgressTrack,
                             isVisible && styles.connectorVisible,
                         )}
                         thickness="medium"
                         value={progress}
                         aria-hidden="true"/>
            {isActive && (
                <HowItWorksPauseButton id={`how-it-works-pause-button-${step.number}`}
                                       isPaused={isPaused}
                                       onTogglePause={onTogglePause}/>
            )}
            {isLast && (
                <div id={`how-it-works-completion-dot-${step.number}`}
                     className={mergeClasses(
                         styles.stepCompletionDot,
                         isActive && progress >= 1 && styles.stepCompletionDotVisible,
                     )}
                     aria-hidden="true"/>
            )}
        </div>
    );
}
