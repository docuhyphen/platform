import {Button, MessageBar, MessageBarActions, MessageBarBody} from "@fluentui/react-components";
import {DismissRegular} from "@fluentui/react-icons";

interface IndividualOnboardingErrorBarProps
{
    message: string;
    onDismiss: () => void;
}

const IndividualOnboardingErrorBar = ({message, onDismiss}: IndividualOnboardingErrorBarProps) =>
{
    if (!message) return null;

    return (
        <MessageBar
            id={"individual-onboarding-error-bar"}
            intent={"error"}>
            <MessageBarBody id={"individual-onboarding-error-body"}>
                {message}
            </MessageBarBody>
            <MessageBarActions
                id={"individual-onboarding-error-actions"}
                containerAction={
                    <Button
                        id={"individual-onboarding-dismiss-error-btn"}
                        onClick={onDismiss}
                        appearance="transparent"
                        shape={"circular"}
                        icon={<DismissRegular/>}
                    />
                }
            />
        </MessageBar>
    );
};

export default IndividualOnboardingErrorBar;
