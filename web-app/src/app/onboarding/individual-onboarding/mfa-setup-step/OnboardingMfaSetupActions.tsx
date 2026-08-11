import {Button, Spinner} from "@fluentui/react-components";

interface OnboardingMfaSetupActionsProps
{
    actionsClassName: string;
    buttonWithLoadingClassName: string;
    busy: boolean;
    canSubmit: boolean;
    enrolling: boolean;
    onPrimary: () => void;
    onSkip: () => void;
}

const OnboardingMfaSetupActions = ({
    actionsClassName,
    buttonWithLoadingClassName,
    busy,
    canSubmit,
    enrolling,
    onPrimary,
    onSkip,
}: OnboardingMfaSetupActionsProps) => (
    <div
        id={"individual-onboarding-mfa-actions"}
        className={actionsClassName}>
        <Button
            id={"individual-onboarding-mfa-skip-btn"}
            appearance={"subtle"}
            shape={"circular"}
            disabled={busy}
            onClick={onSkip}>
            Skip for later
        </Button>
        <Button
            id={"individual-onboarding-mfa-primary-btn"}
            appearance={"primary"}
            shape={"circular"}
            className={buttonWithLoadingClassName}
            disabled={busy || !canSubmit}
            onClick={onPrimary}>
            {busy &&
                <Spinner
                    id={"individual-onboarding-mfa-action-spinner"}
                    size={"tiny"}
                />}
            {enrolling ? "Verify and enable" : "Continue"}
        </Button>
    </div>
);

export default OnboardingMfaSetupActions;
