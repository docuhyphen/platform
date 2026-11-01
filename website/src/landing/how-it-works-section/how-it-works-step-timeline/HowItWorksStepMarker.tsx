import {Text, mergeClasses} from "@fluentui/react-components";
import {useHowItWorksStepTimelineStyles} from "./HowItWorksStepTimelineStyles.tsx";

interface HowItWorksStepMarkerProps
{
    id: string;
    number: string;
    isActive: boolean;
    isLast?: boolean;
}

export function HowItWorksStepMarker({id, number, isActive, isLast = false}: HowItWorksStepMarkerProps)
{
    const styles = useHowItWorksStepTimelineStyles();

    return (
        <div id={id}
             className={mergeClasses(
                 styles.stepMarker,
                 isLast && styles.lastStepMarker,
                 isActive && styles.activeStepMarker,
             )}>
            <Text id={`${id}-number`}
                  className={styles.number}>{number}</Text>
        </div>
    );
}
