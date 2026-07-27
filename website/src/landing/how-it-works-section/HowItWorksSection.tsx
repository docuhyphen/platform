import {
    Button,
    Carousel,
    CarouselCard,
    CarouselSlider,
    mergeClasses,
    ProgressBar,
    Text,
    Title2,
    Title3,
} from "@fluentui/react-components";
import {ChevronLeft20Regular, Pause16Regular, Play16Regular} from "@fluentui/react-icons";
import {useEffect, useRef, useState} from "react";
import {useHowItWorksSectionStyles} from "./HowItWorksSectionStyles.tsx";

type HowItWorksStep = {
    number: string;
    title: string;
    description: string;
    illustrationLabel: string;
    illustrationClass: string;
    animationClass: string;
};

const steps: HowItWorksStep[] = [
    {
        number: "01",
        title: "Create an Exchange",
        description: "Bring together the documents, people, and context for a single piece of work.",
        illustrationLabel: "A secure document Exchange being created",
        illustrationClass: "illustrationOne",
        animationClass: "stepFirst",
    },
    {
        number: "02",
        title: "Set access and workflow",
        description: "Assign the right roles and configure the workflow that guides each Exchange.",
        illustrationLabel: "People, access controls, and workflow steps",
        illustrationClass: "illustrationTwo",
        animationClass: "stepSecond",
    },
    {
        number: "03",
        title: "Work through the process",
        description: "Participants collaborate, review, respond, and complete the steps relevant to them.",
        illustrationLabel: "Participants collaborating on a document",
        illustrationClass: "illustrationThree",
        animationClass: "stepThird",
    },
    {
        number: "04",
        title: "Finish with a clear record",
        description: "Close the Exchange with an auditable history of activity, decisions, and outcomes.",
        illustrationLabel: "A completed Exchange with an activity record",
        illustrationClass: "illustrationFour",
        animationClass: "stepFourth",
    },
];

const STEP_ROTATION_INTERVAL_MS = 8000;

export function HowItWorksSection()
{
    const styles = useHowItWorksSectionStyles();
    const sectionRef = useRef<HTMLElement>(null);
    const rotationTimeoutRef = useRef<number | null>(null);
    const rotationStartedAtRef = useRef<number | null>(null);
    const rotationRemainingMsRef = useRef(STEP_ROTATION_INTERVAL_MS);
    const progressIntervalRef = useRef<number | null>(null);
    const [isVisible, setIsVisible] = useState(false);
    const [activeStepIndex, setActiveStepIndex] = useState(0);
    const [isPaused, setIsPaused] = useState(false);
    const [activeStepProgress, setActiveStepProgress] = useState(0);

    useEffect(() =>
    {
        const section = sectionRef.current;
        if (!section) return;

        const observer = new IntersectionObserver(
            ([entry]) =>
            {
                if (!entry.isIntersecting) return;
                setIsVisible(true);
                observer.unobserve(entry.target);
            },
            {threshold: 0.3},
        );

        observer.observe(section);
        return () => observer.disconnect();
    }, []);

    const clearRotationTimeout = () => {
        if (rotationTimeoutRef.current === null) return;
        window.clearTimeout(rotationTimeoutRef.current);
        rotationTimeoutRef.current = null;
    };

    const clearProgressInterval = () => {
        if (progressIntervalRef.current === null) return;
        window.clearInterval(progressIntervalRef.current);
        progressIntervalRef.current = null;
    };

    const startRotationTimeout = (delayMs: number) => {
        clearRotationTimeout();
        rotationStartedAtRef.current = window.performance.now();
        rotationTimeoutRef.current = window.setTimeout(() =>
        {
            rotationRemainingMsRef.current = STEP_ROTATION_INTERVAL_MS;
            setActiveStepProgress(0);
            setActiveStepIndex((currentIndex) => (currentIndex + 1) % steps.length);
        }, delayMs);
    };

    const startProgressAnimation = (remainingMs: number) => {
        clearProgressInterval();

        const progressOffset = 1 - (remainingMs / STEP_ROTATION_INTERVAL_MS);
        const updateProgress = () => {
            if (rotationStartedAtRef.current === null) return;

            const elapsedMs = window.performance.now() - rotationStartedAtRef.current;
            const nextProgress = Math.min(1, progressOffset + (elapsedMs / STEP_ROTATION_INTERVAL_MS));
            setActiveStepProgress(nextProgress);

            if (nextProgress >= 1)
            {
                clearProgressInterval();
            }
        };

        setActiveStepProgress(progressOffset);
        updateProgress();
        progressIntervalRef.current = window.setInterval(updateProgress, 100);
    };

    useEffect(() =>
    {
        if (isPaused)
        {
            clearRotationTimeout();
            clearProgressInterval();
            return;
        }

        startRotationTimeout(rotationRemainingMsRef.current);
        startProgressAnimation(rotationRemainingMsRef.current);

        return () =>
        {
            clearRotationTimeout();
            clearProgressInterval();
        };
    }, [activeStepIndex, isPaused]);

    const updateActiveStepIndex = (nextIndex: number) => {
        rotationRemainingMsRef.current = STEP_ROTATION_INTERVAL_MS;
        setActiveStepProgress(0);
        setActiveStepIndex(nextIndex);
    };

    const goToPreviousStep = () => {
        updateActiveStepIndex(activeStepIndex === 0 ? steps.length - 1 : activeStepIndex - 1);
    };

    const goToNextStep = () => {
        updateActiveStepIndex((activeStepIndex + 1) % steps.length);
    };

    const togglePause = () => {
        if (!isPaused)
        {
            if (rotationStartedAtRef.current !== null)
            {
                const elapsedMs = window.performance.now() - rotationStartedAtRef.current;
                rotationRemainingMsRef.current = Math.max(0, rotationRemainingMsRef.current - elapsedMs);
            }

            clearRotationTimeout();
            clearProgressInterval();
            setIsPaused(true);
            return;
        }

        rotationRemainingMsRef.current = Math.max(rotationRemainingMsRef.current, 1);
        setIsPaused(false);
    };

    return (
        <section
            id="how-it-works-section"
            className={styles.section}
            aria-labelledby="how-it-works-heading"
            ref={sectionRef}
        >
            <div className={styles.container}>
                <div className={styles.intro}>
                    <Title2 id="how-it-works-heading" className={styles.title}>How it works</Title2>
                    <Text size={500} className={styles.subtitle}>
                        Move every document process from first invitation to a clear, auditable outcome.
                    </Text>
                </div>

                <div className={styles.desktopSteps}>
                    {steps.map((step, index) => (
                        <article
                            key={step.number}
                            id={`how-it-works-step-${step.number}`}
                            className={mergeClasses(
                                styles.step,
                                styles[step.animationClass as keyof typeof styles],
                                isVisible && styles.stepVisible,
                            )}
                        >
                            <div
                                className={mergeClasses(
                                    styles.stepTimeline,
                                )}
                            >
                                <div
                                    className={mergeClasses(
                                        styles.stepMarker,
                                        index === steps.length - 1 && styles.lastStepMarker,
                                        index === activeStepIndex && styles.activeStepMarker,
                                    )}
                                >
                                    <Text className={styles.number}>{step.number}</Text>
                                </div>
                                <ProgressBar
                                    id={`how-it-works-progress-${step.number}`}
                                    className={mergeClasses(
                                        styles.stepProgressBar,
                                        index === steps.length - 1 && styles.lastStepProgressTrack,
                                        isVisible && styles.connectorVisible,
                                    )}
                                    thickness="medium"
                                    value={
                                        index < activeStepIndex
                                            ? 1
                                            : index === activeStepIndex
                                                ? activeStepProgress
                                                : 0
                                    }
                                    aria-hidden="true"
                                />
                                {index === activeStepIndex && (
                                    <Button
                                        id={`how-it-works-pause-button-${step.number}`}
                                        appearance="secondary"
                                        shape="circular"
                                        size="small"
                                        className={styles.pauseButton}
                                        icon={isPaused ? <Play16Regular/> : <Pause16Regular/>}
                                        onClick={togglePause}
                                        aria-label={isPaused ? "Resume rotation" : "Pause rotation"}
                                    />
                                )}
                                {index === steps.length - 1 && (
                                    <div
                                        className={mergeClasses(
                                            styles.stepCompletionDot,
                                            index === activeStepIndex &&
                                            activeStepProgress >= 1 &&
                                            styles.stepCompletionDotVisible,
                                        )}
                                        aria-hidden="true"
                                    />
                                )}
                            </div>
                            <div
                                className={mergeClasses(
                                    styles.stepCard,
                                    index === activeStepIndex && styles.activeStepCard,
                                )}
                            >
                                <div
                                    className={mergeClasses(
                                        styles[step.illustrationClass as keyof typeof styles],
                                        index === activeStepIndex && styles.activeIllustration,
                                    )}
                                    role="img"
                                    aria-label={step.illustrationLabel}
                                />
                                <Text
                                    className={mergeClasses(
                                        styles.stepLabel,
                                        index === activeStepIndex && styles.activeStepText,
                                    )}
                                >
                                    Step {step.number}
                                </Text>
                                <Title3
                                    className={mergeClasses(
                                        styles.stepTitle,
                                        index === activeStepIndex && styles.activeStepText,
                                    )}
                                >
                                    {step.title}
                                </Title3>
                                <Text
                                    className={mergeClasses(
                                        styles.stepDescription,
                                        index === activeStepIndex && styles.activeStepText,
                                    )}
                                >
                                    {step.description}
                                </Text>
                            </div>
                        </article>
                    ))}
                </div>

                <div className={styles.mobileCarouselWrapper}>
                    <Carousel
                        id="how-it-works-mobile-carousel"
                        className={styles.mobileCarousel}
                        activeIndex={activeStepIndex}
                        align="center"
                        draggable
                        groupSize={1}
                        whitespace
                        onActiveIndexChange={(_, data) => updateActiveStepIndex(data.index)}
                        announcement={(index, totalSlides) => `Step ${index + 1} of ${totalSlides}`}
                    >
                        <CarouselSlider
                            id="how-it-works-mobile-carousel-slider"
                            className={styles.mobileCarouselSlider}
                        >
                            {steps.map((step, index) => (
                                <CarouselCard
                                    key={step.number}
                                    id={`how-it-works-mobile-step-${step.number}`}
                                    className={styles.mobileCarouselCard}
                                >
                                    <div className={styles.mobileStepHeader}>
                                        <div
                                            className={mergeClasses(
                                                styles.stepMarker,
                                                index === activeStepIndex && styles.activeStepMarker,
                                            )}
                                        >
                                            <Text className={styles.number}>{step.number}</Text>
                                        </div>
                                        <ProgressBar
                                            id={`how-it-works-mobile-progress-${step.number}`}
                                            className={mergeClasses(
                                                styles.mobileStepProgressBar,
                                                index === steps.length - 1 && styles.lastStepProgressTrack,
                                            )}
                                            thickness="medium"
                                            value={
                                                index < activeStepIndex
                                                    ? 1
                                                    : index === activeStepIndex
                                                        ? activeStepProgress
                                                        : 0
                                            }
                                            aria-hidden="true"
                                        />
                                        {index === steps.length - 1 && (
                                            <div
                                                className={mergeClasses(
                                                    styles.mobileStepCompletionDot,
                                                    index === activeStepIndex &&
                                                    activeStepProgress >= 1 &&
                                                    styles.stepCompletionDotVisible,
                                                )}
                                                aria-hidden="true"
                                            />
                                        )}
                                        {index === activeStepIndex && (
                                            <Button
                                                id={`how-it-works-mobile-pause-button-${step.number}`}
                                                appearance="secondary"
                                                shape="circular"
                                                size="small"
                                                className={styles.pauseButton}
                                                icon={isPaused ? <Play16Regular/> : <Pause16Regular/>}
                                                onClick={togglePause}
                                                aria-label={isPaused ? "Resume rotation" : "Pause rotation"}
                                            />
                                        )}
                                    </div>
                                    <div
                                        className={mergeClasses(
                                            styles.stepCard,
                                            index === activeStepIndex && styles.activeStepCard,
                                        )}
                                    >
                                        <div
                                            className={mergeClasses(
                                                styles[step.illustrationClass as keyof typeof styles],
                                                index === activeStepIndex && styles.activeIllustration,
                                            )}
                                            role="img"
                                            aria-label={step.illustrationLabel}
                                        />
                                        <Text
                                            className={mergeClasses(
                                                styles.stepLabel,
                                                index === activeStepIndex && styles.activeStepText,
                                            )}
                                        >
                                            Step {step.number}
                                        </Text>
                                        <Title3
                                            className={mergeClasses(
                                                styles.stepTitle,
                                                index === activeStepIndex && styles.activeStepText,
                                            )}
                                        >
                                            {step.title}
                                        </Title3>
                                        <Text
                                            className={mergeClasses(
                                                styles.stepDescription,
                                                index === activeStepIndex && styles.activeStepText,
                                            )}
                                        >
                                            {step.description}
                                        </Text>
                                    </div>
                                </CarouselCard>
                            ))}
                        </CarouselSlider>
                    </Carousel>

                    <div className={styles.mobileNavigation}>
                        <Button
                            id="how-it-works-previous-step-button"
                            appearance="secondary"
                            shape="circular"
                            className={styles.mobileNavButton}
                            icon={<ChevronLeft20Regular/>}
                            onClick={goToPreviousStep}
                            size="small"
                            aria-label="Previous step"
                        />
                        <Text
                            id="how-it-works-mobile-step-count"
                            className={styles.mobileStepCount}
                        >
                            {activeStepIndex + 1}/{steps.length}
                        </Text>
                        <Button
                            id="how-it-works-next-step-button"
                            appearance="primary"
                            shape="circular"
                            className={styles.mobileNavButton}
                            onClick={goToNextStep}
                            size="small"
                        >
                            Next step
                        </Button>
                    </div>
                </div>
            </div>
        </section>
    );
}
