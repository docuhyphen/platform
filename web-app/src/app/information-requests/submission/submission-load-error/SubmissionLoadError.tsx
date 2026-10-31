import {Button, MessageBar, MessageBarActions, MessageBarBody} from "@fluentui/react-components";

interface Props {
    message: string;
    onRetry: () => void;
}

const SubmissionLoadError = ({message, onRetry}: Props) => (
    <MessageBar id={"information-request-submission-load-error"}
                intent={"error"}>
        <MessageBarBody id={"information-request-submission-load-error-body"}>{message}</MessageBarBody>
        <MessageBarActions id={"information-request-submission-load-error-actions"}>
            <Button id={"information-request-submission-retry"}
                    appearance={"secondary"}
                    shape={"circular"}
                    size={"small"}
                    onClick={onRetry}>
                Try again
            </Button>
        </MessageBarActions>
    </MessageBar>
);

export default SubmissionLoadError;
