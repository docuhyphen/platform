import {
    Button,
    Text,
    TeachingPopover,
    TeachingPopoverBody,
    TeachingPopoverFooter,
    TeachingPopoverHeader,
    TeachingPopoverSurface,
    TeachingPopoverTitle,
    TeachingPopoverTrigger,
} from '@fluentui/react-components';
import {useTourCoachStyles} from "./TourCoachStyles.tsx";
import {useTourCoach} from './useTourCoach.ts';

function TourCoach()
{
    const styles = useTourCoachStyles();
    const {
        isOpen,
        currentStep,
        targetEl,
        step,
        isLast,
        stepCount,
        completeTour,
        handleNext,
        handlePrev,
        onOpenChange,
    } = useTourCoach();

    if (!isOpen || !targetEl) return null;

    return (
        <TeachingPopover
            open
            withArrow
            onOpenChange={(_e, data) => onOpenChange(data.open)}
            positioning={{
                target: targetEl,
                position: step.position ?? 'below',
                align: step.align ?? 'center',
                offset: 12,
            }}
        >
            <TeachingPopoverTrigger>
                <span id={"tour-coach-span-1"}
                      className={styles.hiddenAnchor}/>
            </TeachingPopoverTrigger>
            <TeachingPopoverSurface id={"tour-coach-teaching-popover-surface-1"}
                                    className={styles.popoverSurface}>
                <TeachingPopoverHeader id={"tour-coach-teaching-popover-header-1"}>
                    Step {currentStep + 1} of {stepCount}
                </TeachingPopoverHeader>
                <TeachingPopoverBody id={"tour-coach-teaching-popover-body-1"}>
                    <TeachingPopoverTitle id={"tour-coach-teaching-popover-title-1"}>{step.title}</TeachingPopoverTitle>
                    <Text id={"tour-coach-text-1"}
                          size={200}>{step.description}</Text>
                </TeachingPopoverBody>
                <TeachingPopoverFooter
                    id={"tour-coach-teaching-popover-footer-1"}
                    primary={{children: isLast ? 'Got it' : 'Next', onClick: handleNext}}
                    secondary={currentStep === 0
                        ? {children: 'Skip tour', onClick: completeTour}
                        : undefined}
                />
                {currentStep > 0 && (
                    <div id={"tour-coach-div-1"}
                         className={styles.backButtonRow}>
                        <Button
                            id={"tour-back-btn"}
                            shape={"circular"}
                            appearance="transparent"
                            size="small"
                            onClick={handlePrev}>
                            Back
                        </Button>
                    </div>
                )}
            </TeachingPopoverSurface>
        </TeachingPopover>
    );
}

export default TourCoach;
