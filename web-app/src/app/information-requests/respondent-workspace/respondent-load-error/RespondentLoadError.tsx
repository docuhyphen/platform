import {Button, MessageBar, MessageBarActions, MessageBarBody} from "@fluentui/react-components";

interface Props {
    message: string;
    onRetry?: () => void;
}

const RespondentLoadError = ({message, onRetry}: Props) => (
    <MessageBar id={"information-request-respondent-error"}
                intent={"error"}
                role={"alert"}>
        <MessageBarBody id={"information-request-respondent-error-body"}>{message}</MessageBarBody>
        {onRetry && (
            <MessageBarActions id={"information-request-respondent-error-actions"}>
                <Button id={"information-request-respondent-retry"}
                        appearance={"secondary"}
                        shape={"circular"}
                        size={"small"}
                        onClick={onRetry}>
                    Try again
                </Button>
            </MessageBarActions>
        )}
    </MessageBar>
);

export default RespondentLoadError;
