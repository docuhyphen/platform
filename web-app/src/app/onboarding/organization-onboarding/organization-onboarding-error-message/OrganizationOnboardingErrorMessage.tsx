import {Button, MessageBar, MessageBarActions, MessageBarBody} from "@fluentui/react-components";
import {DismissRegular} from "@fluentui/react-icons";

interface OrganizationOnboardingErrorMessageProps
{
    message?: string;
    onDismiss: () => void;
}

const OrganizationOnboardingErrorMessage = ({message, onDismiss}: OrganizationOnboardingErrorMessageProps) =>
{
    if (!message)
    {
        return null;
    }

    return (
        <MessageBar id={"organization-onboarding-form-message-bar-1"}
                    intent={"error"}>
            <MessageBarBody id={"organization-onboarding-form-message-bar-body-1"}>
                {message}
            </MessageBarBody>
            <MessageBarActions
                id={"organization-onboarding-form-message-bar-actions-1"}
                containerAction={
                    <Button
                        id={"org-onboarding-dismiss-error-btn"}
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

export default OrganizationOnboardingErrorMessage;
