import {mergeClasses} from "@fluentui/react-components";
import {HowItWorksStepCard} from "../how-it-works-step-card/HowItWorksStepCard.tsx";
import {HowItWorksStepTimeline} from "../how-it-works-step-timeline/HowItWorksStepTimeline.tsx";
import {getStepProgress, howItWorksSteps} from "../howItWorksSteps.ts";
import {useHowItWorksDesktopStepsStyles} from "./HowItWorksDesktopStepsStyles.tsx";

interface HowItWorksDesktopStepsProps
{
    activeStepIndex: number;
    activeStepProgress: number;
    isPaused: boolean;
    isVisible: boolean;
    onTogglePause: () => void;
}

export function HowItWorksDesktopSteps({
    activeStepIndex,
    activeStepProgress,
    isPaused,
    isVisible,
    onTogglePause,
}: HowItWorksDesktopStepsProps)
{
    const styles = useHowItWorksDesktopStepsStyles();

    return (
        <div id={"how-it-works-desktop-steps"}
             className={styles.desktopSteps}>
            {howItWorksSteps.map((step, index) => (
                <article key={step.number}
                         id={`how-it-works-step-${step.number}`}
                         className={mergeClasses(
                             styles.step,
                             styles[step.animationClass],
                             isVisible && styles.stepVisible,
                         )}>
                    <HowItWorksStepTimeline step={step}
                                            isLast={index === howItWorksSteps.length - 1}
                                            isActive={index === activeStepIndex}
                                            isPaused={isPaused}
                                            isVisible={isVisible}
                                            progress={getStepProgress(index, activeStepIndex, activeStepProgress)}
                                            onTogglePause={onTogglePause}/>
                    <HowItWorksStepCard step={step}
                                        isActive={index === activeStepIndex}
                                        idPrefix="how-it-works-step"/>
                </article>
            ))}
        </div>
    );
}
