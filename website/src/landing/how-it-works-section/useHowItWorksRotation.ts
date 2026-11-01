import {useCallback, useEffect, useRef, useState} from "react";
import {howItWorksSteps, STEP_ROTATION_INTERVAL_MS} from "./howItWorksSteps.ts";

export function useHowItWorksRotation()
{
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

    const clearRotationTimeout = useCallback(() =>
    {
        if (rotationTimeoutRef.current === null) return;
        window.clearTimeout(rotationTimeoutRef.current);
        rotationTimeoutRef.current = null;
    }, []);

    const clearProgressInterval = useCallback(() =>
    {
        if (progressIntervalRef.current === null) return;
        window.clearInterval(progressIntervalRef.current);
        progressIntervalRef.current = null;
    }, []);

    const startRotationTimeout = useCallback((delayMs: number) =>
    {
        clearRotationTimeout();
        rotationStartedAtRef.current = window.performance.now();
        rotationTimeoutRef.current = window.setTimeout(() =>
        {
            rotationRemainingMsRef.current = STEP_ROTATION_INTERVAL_MS;
            setActiveStepProgress(0);
            setActiveStepIndex((currentIndex) => (currentIndex + 1) % howItWorksSteps.length);
        }, delayMs);
    }, [clearRotationTimeout]);

    const startProgressAnimation = useCallback((remainingMs: number) =>
    {
        clearProgressInterval();

        const progressOffset = 1 - (remainingMs / STEP_ROTATION_INTERVAL_MS);
        const updateProgress = () =>
        {
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
    }, [clearProgressInterval]);

    useEffect(() =>
    {
        if (!isVisible || isPaused)
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
    }, [
        activeStepIndex,
        isPaused,
        isVisible,
        clearProgressInterval,
        clearRotationTimeout,
        startProgressAnimation,
        startRotationTimeout,
    ]);

    const updateActiveStepIndex = (nextIndex: number) =>
    {
        rotationRemainingMsRef.current = STEP_ROTATION_INTERVAL_MS;
        setActiveStepProgress(0);
        setActiveStepIndex(nextIndex);
    };

    const goToPreviousStep = () =>
    {
        updateActiveStepIndex(activeStepIndex === 0 ? howItWorksSteps.length - 1 : activeStepIndex - 1);
    };

    const goToNextStep = () =>
    {
        updateActiveStepIndex((activeStepIndex + 1) % howItWorksSteps.length);
    };

    const togglePause = () =>
    {
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

    return {
        sectionRef,
        isVisible,
        activeStepIndex,
        isPaused,
        activeStepProgress,
        updateActiveStepIndex,
        goToPreviousStep,
        goToNextStep,
        togglePause,
    };
}
