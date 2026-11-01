import {Button} from "@fluentui/react-components";
import {Pause16Regular, Play16Regular} from "@fluentui/react-icons";
import {useHowItWorksStepTimelineStyles} from "./HowItWorksStepTimelineStyles.tsx";

interface HowItWorksPauseButtonProps
{
    id: string;
    isPaused: boolean;
    onTogglePause: () => void;
}

export function HowItWorksPauseButton({id, isPaused, onTogglePause}: HowItWorksPauseButtonProps)
{
    const styles = useHowItWorksStepTimelineStyles();

    return (
        <Button id={id}
                appearance="secondary"
                shape="circular"
                size="small"
                className={styles.pauseButton}
                icon={isPaused
                    ? <Play16Regular id={`${id}-play-icon`}/>
                    : <Pause16Regular id={`${id}-pause-icon`}/>}
                onClick={onTogglePause}
                aria-label={isPaused ? "Resume rotation" : "Pause rotation"}/>
    );
}
