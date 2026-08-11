import {Text} from "@fluentui/react-components";

interface OnboardingMfaSetupHeaderProps
{
    headerClassName: string;
}

const OnboardingMfaSetupHeader = ({headerClassName}: OnboardingMfaSetupHeaderProps) => (
    <div
        id={"individual-onboarding-mfa-header"}
        className={headerClassName}>
        <Text
            id={"individual-onboarding-mfa-title"}
            size={500}
            weight={"semibold"}>
            Set up Multi-Factor Authentication
        </Text>
    </div>
);

export default OnboardingMfaSetupHeader;
