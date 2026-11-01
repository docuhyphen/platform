import {Text, Title2} from "@fluentui/react-components";
import {HowItWorksDesktopSteps} from "./how-it-works-desktop-steps/HowItWorksDesktopSteps.tsx";
import {HowItWorksMobileCarousel} from "./how-it-works-mobile-carousel/HowItWorksMobileCarousel.tsx";
import {useHowItWorksSectionStyles} from "./HowItWorksSectionStyles.tsx";
import {useHowItWorksRotation} from "./useHowItWorksRotation.ts";

export function HowItWorksSection()
{
    const styles = useHowItWorksSectionStyles();
    const {
        sectionRef,
        isVisible,
        activeStepIndex,
        isPaused,
        activeStepProgress,
        updateActiveStepIndex,
        goToPreviousStep,
        goToNextStep,
        togglePause,
    } = useHowItWorksRotation();

    return (
        <section id={"how-it-works-section"}
                 className={styles.section}
                 aria-labelledby="how-it-works-heading"
                 ref={sectionRef}>
            <div id={"how-it-works-container"}
                 className={styles.container}>
                <div id={"how-it-works-intro"}
                     className={styles.intro}>
                    <Title2 id={"how-it-works-heading"}
                            className={styles.title}>How it works</Title2>
                    <Text id={"how-it-works-subtitle"}
                          size={500}
                          className={styles.subtitle}>
                        Move every document process from first invitation to a clear, auditable outcome.
                    </Text>
                </div>

                <HowItWorksDesktopSteps activeStepIndex={activeStepIndex}
                                        activeStepProgress={activeStepProgress}
                                        isPaused={isPaused}
                                        isVisible={isVisible}
                                        onTogglePause={togglePause}/>

                <HowItWorksMobileCarousel activeStepIndex={activeStepIndex}
                                          activeStepProgress={activeStepProgress}
                                          isPaused={isPaused}
                                          onTogglePause={togglePause}
                                          onSelectStep={updateActiveStepIndex}
                                          onPrevious={goToPreviousStep}
                                          onNext={goToNextStep}/>
            </div>
        </section>
    );
}
