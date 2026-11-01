import {ProgressBar, mergeClasses} from "@fluentui/react-components";
import type {HowItWorksStep} from "../howItWorksSteps.ts";
import {HowItWorksPauseButton} from "./HowItWorksPauseButton.tsx";
import {HowItWorksStepMarker} from "./HowItWorksStepMarker.tsx";
import {useHowItWorksStepTimelineStyles} from "./HowItWorksStepTimelineStyles.tsx";

interface HowItWorksMobileStepHeaderProps
{
    step: HowItWorksStep;
    isLast: boolean;
    isActive: boolean;
    isPaused: boolean;
    progress: number;
    onTogglePause: () => void;
}

export function HowItWorksMobileStepHeader({
    step,
    isLast,
    isActive,
    isPaused,
    progress,
    onTogglePause,
}: HowItWorksMobileStepHeaderProps)
{
    const styles = useHowItWorksStepTimelineStyles();

    return (
        <div id={`how-it-works-mobile-step-header-${step.number}`}
             className={styles.mobileStepHeader}>
            <HowItWorksStepMarker id={`how-it-works-mobile-marker-${step.number}`}
                                  number={step.number}
                                  isActive={isActive}/>
            <ProgressBar id={`how-it-works-mobile-progress-${step.number}`}
                         className={mergeClasses(
                             styles.mobileStepProgressBar,
                             isLast && styles.lastStepProgressTrack,
                         )}
                         thickness="medium"
                         value={progress}
                         aria-hidden="true"/>
            {isLast && (
                <div id={`how-it-works-mobile-completion-dot-${step.number}`}
                     className={mergeClasses(
                         styles.mobileStepCompletionDot,
                         isActive && progress >= 1 && styles.stepCompletionDotVisible,
                     )}
                     aria-hidden="true"/>
            )}
            {isActive && (
                <HowItWorksPauseButton id={`how-it-works-mobile-pause-button-${step.number}`}
                                       isPaused={isPaused}
                                       onTogglePause={onTogglePause}/>
            )}
        </div>
    );
}
