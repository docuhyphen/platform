import {useCallback, useEffect, useRef, useState} from 'react';
import {useAuth} from '../../../context/AuthContext';
import {updateAppUserSettings} from '../../../services/appUserApi';
import {FORCE_TOUR_VISIBLE, TOUR_STEPS} from './tourSteps.ts';

export const useTourCoach = () =>
{
    const {appUser, token} = useAuth();
    const [isOpen, setIsOpen] = useState(false);
    const [currentStep, setCurrentStep] = useState(0);
    const [targetEl, setTargetEl] = useState<HTMLElement | null>(null);
    const startedRef = useRef(false);
    const steppingRef = useRef(false);

    useEffect(() =>
    {
        return;

        if (!appUser?.person) return;
        if (startedRef.current) return;

        const shouldShow = FORCE_TOUR_VISIBLE || !appUser.settings?.tourCompleted;
        if (!shouldShow) return;

        startedRef.current = true;
        const timer = setTimeout(() =>
        {
            setCurrentStep(0);
            setIsOpen(true);
        }, 700);
        return () => clearTimeout(timer);
    }, [appUser]);

    useEffect(() =>
    {
        if (!isOpen) return;

        const step = TOUR_STEPS[currentStep];
        let cancelled = false;

        const tryResolve = (attempt = 0) =>
        {
            if (cancelled) return;
            const el = document.getElementById(step.targetId);
            if (el)
            {
                setTargetEl(el);
                steppingRef.current = false;
            }
            else if (attempt < 20)
            {
                requestAnimationFrame(() => tryResolve(attempt + 1));
            }
        };

        tryResolve();
        return () =>
        {
            cancelled = true;
        };
    }, [currentStep, isOpen]);

    const completeTour = useCallback(async () =>
    {
        setIsOpen(false);
        setTargetEl(null);

        if (appUser?.settings && token)
        {
            try
            {
                await updateAppUserSettings(
                    {...appUser.settings, tourCompleted: true},
                    token,
                );
            }
            catch
            {
            }
        }
    }, [appUser, token]);

    const handleNext = useCallback(() =>
    {
        if (currentStep < TOUR_STEPS.length - 1)
        {
            steppingRef.current = true;
            setTargetEl(null);
            setCurrentStep((s) => s + 1);
        }
        else
        {
            completeTour();
        }
    }, [currentStep, completeTour]);

    const handlePrev = useCallback(() =>
    {
        if (currentStep > 0)
        {
            steppingRef.current = true;
            setTargetEl(null);
            setCurrentStep((s) => s - 1);
        }
    }, [currentStep]);

    const onOpenChange = (open: boolean) =>
    {
        if (!open && !steppingRef.current)
        {
            completeTour();
        }
    };

    return {
        isOpen,
        currentStep,
        targetEl,
        step: TOUR_STEPS[currentStep],
        isLast: currentStep === TOUR_STEPS.length - 1,
        stepCount: TOUR_STEPS.length,
        completeTour,
        handleNext,
        handlePrev,
        onOpenChange,
    };
};
