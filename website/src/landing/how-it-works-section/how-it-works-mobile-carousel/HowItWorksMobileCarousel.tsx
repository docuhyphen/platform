import {Button, Carousel, CarouselCard, CarouselSlider, Text} from "@fluentui/react-components";
import {ChevronLeft20Regular} from "@fluentui/react-icons";
import {HowItWorksStepCard} from "../how-it-works-step-card/HowItWorksStepCard.tsx";
import {HowItWorksMobileStepHeader} from "../how-it-works-step-timeline/HowItWorksMobileStepHeader.tsx";
import {getStepProgress, howItWorksSteps} from "../howItWorksSteps.ts";
import {useHowItWorksMobileCarouselStyles} from "./HowItWorksMobileCarouselStyles.tsx";

interface HowItWorksMobileCarouselProps
{
    activeStepIndex: number;
    activeStepProgress: number;
    isPaused: boolean;
    onTogglePause: () => void;
    onSelectStep: (index: number) => void;
    onPrevious: () => void;
    onNext: () => void;
}

export function HowItWorksMobileCarousel({
    activeStepIndex,
    activeStepProgress,
    isPaused,
    onTogglePause,
    onSelectStep,
    onPrevious,
    onNext,
}: HowItWorksMobileCarouselProps)
{
    const styles = useHowItWorksMobileCarouselStyles();

    return (
        <div id={"how-it-works-mobile-carousel-wrapper"}
             className={styles.mobileCarouselWrapper}>
            <Carousel id={"how-it-works-mobile-carousel"}
                      className={styles.mobileCarousel}
                      activeIndex={activeStepIndex}
                      align="center"
                      draggable
                      groupSize={1}
                      whitespace
                      onActiveIndexChange={(_, data) => onSelectStep(data.index)}
                      announcement={(index, totalSlides) => `Step ${index + 1} of ${totalSlides}`}>
                <CarouselSlider id={"how-it-works-mobile-carousel-slider"}
                                className={styles.mobileCarouselSlider}>
                    {howItWorksSteps.map((step, index) => (
                        <CarouselCard key={step.number}
                                      id={`how-it-works-mobile-step-${step.number}`}
                                      className={styles.mobileCarouselCard}>
                            <HowItWorksMobileStepHeader step={step}
                                                        isLast={index === howItWorksSteps.length - 1}
                                                        isActive={index === activeStepIndex}
                                                        isPaused={isPaused}
                                                        progress={getStepProgress(index, activeStepIndex, activeStepProgress)}
                                                        onTogglePause={onTogglePause}/>
                            <HowItWorksStepCard step={step}
                                                isActive={index === activeStepIndex}
                                                idPrefix="how-it-works-mobile-step"/>
                        </CarouselCard>
                    ))}
                </CarouselSlider>
            </Carousel>

            <div id={"how-it-works-mobile-navigation"}
                 className={styles.mobileNavigation}>
                <Button id={"how-it-works-previous-step-button"}
                        appearance="secondary"
                        shape="circular"
                        className={styles.mobileNavButton}
                        icon={<ChevronLeft20Regular id={"how-it-works-previous-step-icon"}/>}
                        onClick={onPrevious}
                        size="small"
                        aria-label="Previous step"/>
                <Text id={"how-it-works-mobile-step-count"}
                      className={styles.mobileStepCount}>
                    {activeStepIndex + 1}/{howItWorksSteps.length}
                </Text>
                <Button id={"how-it-works-next-step-button"}
                        appearance="primary"
                        shape="circular"
                        className={styles.mobileNavButton}
                        onClick={onNext}
                        size="small">
                    Next step
                </Button>
            </div>
        </div>
    );
}
