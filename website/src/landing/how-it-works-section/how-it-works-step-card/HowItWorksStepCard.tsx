import {Text, Title3, mergeClasses} from "@fluentui/react-components";
import type {HowItWorksStep} from "../howItWorksSteps.ts";
import {useHowItWorksStepCardStyles} from "./HowItWorksStepCardStyles.tsx";

interface HowItWorksStepCardProps
{
    step: HowItWorksStep;
    isActive: boolean;
    idPrefix: string;
}

export function HowItWorksStepCard({step, isActive, idPrefix}: HowItWorksStepCardProps)
{
    const styles = useHowItWorksStepCardStyles();

    return (
        <div id={`${idPrefix}-card-${step.number}`}
             className={mergeClasses(styles.stepCard, isActive && styles.activeStepCard)}>
            <div id={`${idPrefix}-illustration-${step.number}`}
                 className={mergeClasses(styles[step.illustrationClass], isActive && styles.activeIllustration)}
                 role="img"
                 aria-label={step.illustrationLabel}/>
            <Text id={`${idPrefix}-label-${step.number}`}
                  className={mergeClasses(styles.stepLabel, isActive && styles.activeStepText)}>
                Step {step.number}
            </Text>
            <Title3 id={`${idPrefix}-title-${step.number}`}
                    className={mergeClasses(styles.stepTitle, isActive && styles.activeStepText)}>
                {step.title}
            </Title3>
            <Text id={`${idPrefix}-description-${step.number}`}
                  className={mergeClasses(styles.stepDescription, isActive && styles.activeStepText)}>
                {step.description}
            </Text>
        </div>
    );
}
