import { useEffect, useState } from 'react';
import {
    Button,
    Text,
    TeachingPopover,
    TeachingPopoverBody,
    TeachingPopoverFooter,
    TeachingPopoverHeader,
    TeachingPopoverSurface,
    TeachingPopoverTitle,
} from '@fluentui/react-components';
import { useAuth } from '../../../context/AuthContext';

/**
 * Bump the version suffix to re-show the tour for all users when
 * significant new nav features are introduced.
 */
const TOUR_STORAGE_KEY = 'docuhyphen:tour:v1';

interface TourStep {
    targetId: string;
    title: string;
    description: string;
    position?: 'above' | 'below' | 'before' | 'after';
    align?: 'start' | 'center' | 'end';
}

const TOUR_STEPS: TourStep[] = [
    {
        targetId: 'tour-start-sharing',
        title: 'Start Sharing',
        description:
            'This is your primary action. Click "Start Sharing" to request documents from someone or send documents to a colleague. Use the dropdown arrow to choose between requesting and sending.',
        position: 'below',
        align: 'start',
    },
    {
        targetId: 'tour-sessions-btn',
        title: 'Sharing Sessions',
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
    const { appUser } = useAuth();
    const [isOpen, setIsOpen] = useState(false);
    const [currentStep, setCurrentStep] = useState(0);
    const [targetEl, setTargetEl] = useState<HTMLElement | null>(null);

    // Start tour once after the user has completed individual onboarding.
    useEffect(() => {
        if (!appUser?.person) return;
        if (localStorage.getItem(TOUR_STORAGE_KEY)) return;

        // Small delay so all main-menu DOM nodes are rendered.
        const timer = setTimeout(() => setIsOpen(true), 700);
        return () => clearTimeout(timer);
    }, [appUser?.person]);

    // Re-resolve the anchor element whenever the step or open state changes.
    useEffect(() => {
        if (!isOpen) return;
        const el = document.getElementById(TOUR_STEPS[currentStep].targetId);
        setTargetEl(el);
    }, [currentStep, isOpen]);

    const completeTour = () => {
        setIsOpen(false);
        localStorage.setItem(TOUR_STORAGE_KEY, 'true');
    };

    const handleNext = () => {
        if (currentStep < TOUR_STEPS.length - 1) {
            setCurrentStep((s) => s + 1);
        } else {
            completeTour();
        }
    };

    const handlePrev = () => {
        if (currentStep > 0) setCurrentStep((s) => s - 1);
    };

    if (!isOpen) return null;

    const step = TOUR_STEPS[currentStep];
    const isLast = currentStep === TOUR_STEPS.length - 1;

    return (
        <TeachingPopover
            open
            withArrow
            onOpenChange={(_e, data) => {
                if (!data.open) completeTour();
            }}
            positioning={{
                target: targetEl ?? undefined,
                position: step.position ?? 'below',
                align: step.align ?? 'center',
                offset: 12,
            }}
        >
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



