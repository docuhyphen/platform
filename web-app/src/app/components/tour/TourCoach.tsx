import { useEffect, useRef, useState, useCallback } from 'react';
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
import { useAuth } from '../../../context/AuthContext';
import { updateAppUserSettings } from '../../../services/appUserApi';

/**
 * Bump the version suffix to re-show the tour for all users when
 * significant new nav features are introduced.
 *
 * NOTE: For testing, the tour is currently forced visible on every login
 * regardless of server-side `tourCompleted`. Once positioning is verified,
 * revert FORCE_TOUR_VISIBLE to false.
 */
const FORCE_TOUR_VISIBLE = false;

interface TourStep {
    targetId: string;
    title: string;
    description: string;
    position?: 'above' | 'below' | 'before' | 'after';
    align?: 'start' | 'center' | 'end';
}

const TOUR_STEPS: TourStep[] = [
    {
        targetId: 'tour-start-exchanging',
        title: 'Start Sharing',
        description:
            'This is your primary action. Click "Start Exchanging" to request documents from someone or send documents to a colleague. Use the dropdown arrow to choose between requesting and sending.',
        position: 'below',
        align: 'start',
    },
    {
        targetId: 'tour-sessions-btn',
        title: 'Exchanges',
        description:
            'All your active and past document exchanges live here. Click this icon at any time to jump straight back to your sessions list.',
        position: 'below',
        align: 'center',
    },
    {
        targetId: 'tour-notifications',
        title: 'Notifications',
        description:
            'Real-time alerts appear here — new document uploads, session status changes, and responses from your contacts, so nothing slips through the cracks.',
        position: 'below',
        align: 'center',
    },
    {
        targetId: 'tour-account-btn',
        title: 'Your Account',
        description:
            'Tap your profile avatar to access Settings, manage linked sign-in accounts, switch themes, review active sessions, and sign out.',
        position: 'below',
        align: 'end',
    },
];

function TourCoach() {
    const { appUser, token } = useAuth();
    const [isOpen, setIsOpen] = useState(false);
    const [currentStep, setCurrentStep] = useState(0);
    const [targetEl, setTargetEl] = useState<HTMLElement | null>(null);
    const startedRef = useRef(false);
    // Guard: true while we're transitioning between steps so we ignore onOpenChange(false)
    const steppingRef = useRef(false);

    // Decide whether to show the tour when the user is loaded.
    useEffect(() => {

        return;

        if (!appUser?.person) return;
        if (startedRef.current) return;

        const shouldShow = FORCE_TOUR_VISIBLE || !appUser.settings?.tourCompleted;
        if (!shouldShow) return;

        startedRef.current = true;
        const timer = setTimeout(() => {
            setCurrentStep(0);
            setIsOpen(true);
        }, 700);
        return () => clearTimeout(timer);
    }, [appUser]);

    // Re-resolve the anchor element whenever the step or open state changes.
    useEffect(() => {
        if (!isOpen) return;

        const step = TOUR_STEPS[currentStep];
        let cancelled = false;

        const tryResolve = (attempt = 0) => {
            if (cancelled) return;
            const el = document.getElementById(step.targetId);
            if (el) {
                setTargetEl(el);
                // Clear the stepping guard after target is resolved
                steppingRef.current = false;
            } else if (attempt < 20) {
                requestAnimationFrame(() => tryResolve(attempt + 1));
            }
        };

        tryResolve();
        return () => { cancelled = true; };
    }, [currentStep, isOpen]);

    const completeTour = useCallback(async () => {
        setIsOpen(false);
        setTargetEl(null);

        if (appUser?.settings && token) {
            try {
                await updateAppUserSettings(
                    { ...appUser.settings, tourCompleted: true },
                    token,
                );
            } catch {
                // Non-critical
            }
        }
    }, [appUser, token]);

    const handleNext = useCallback(() => {
        if (currentStep < TOUR_STEPS.length - 1) {
            steppingRef.current = true;
            setTargetEl(null); // clear so we don't flash at old position
            setCurrentStep((s) => s + 1);
        } else {
            completeTour();
        }
    }, [currentStep, completeTour]);

    const handlePrev = useCallback(() => {
        if (currentStep > 0) {
            steppingRef.current = true;
            setTargetEl(null);
            setCurrentStep((s) => s - 1);
        }
    }, [currentStep]);

    // Don't render until we have both an open signal and a resolved target.
    if (!isOpen || !targetEl) return null;

    const step = TOUR_STEPS[currentStep];
    const isLast = currentStep === TOUR_STEPS.length - 1;

    return (
        <TeachingPopover
            open
            withArrow
            onOpenChange={(_e, data) => {
                // Only act on genuine user-initiated close (click outside, Escape, etc.)
                // Ignore close events that fire during step transitions.
                if (!data.open && !steppingRef.current) {
                    completeTour();
                }
            }}
            positioning={{
                target: targetEl,
                position: step.position ?? 'below',
                align: step.align ?? 'center',
                offset: 12,
            }}
        >
            <TeachingPopoverTrigger>
                <span style={{ position: 'fixed', top: -9999, left: -9999, width: 0, height: 0 }} />
            </TeachingPopoverTrigger>
            <TeachingPopoverSurface style={{ maxWidth: 340 }}>
                <TeachingPopoverHeader>
                    Step {currentStep + 1} of {TOUR_STEPS.length}
                </TeachingPopoverHeader>
                <TeachingPopoverBody>
                    <TeachingPopoverTitle>{step.title}</TeachingPopoverTitle>
                    <Text size={200}>{step.description}</Text>
                </TeachingPopoverBody>
                <TeachingPopoverFooter
                    primary={{ children: isLast ? 'Got it 🎉' : 'Next →', onClick: handleNext }}
                    secondary={currentStep === 0
                        ? { children: 'Skip tour', onClick: completeTour }
                        : undefined}
                />
                {currentStep > 0 && (
                    <div style={{ paddingBottom: 10, paddingInlineStart: 16 }}>
                        <Button appearance="transparent" size="small" onClick={handlePrev}>
                            ← Back
                        </Button>
                    </div>
                )}
            </TeachingPopoverSurface>
        </TeachingPopover>
    );
}

export default TourCoach;
