import {Button, MessageBar, MessageBarActions, MessageBarBody} from "@fluentui/react-components";

interface Props {
    error: string | null;
    notice: string | null;
    onRetry?: () => void;
}

const AuthorWorkspaceMessages = ({error, notice, onRetry}: Props) => (
    <>
        {error && (
            <MessageBar id={"information-request-author-error"}
                        intent={"error"}
                        role={"alert"}>
                <MessageBarBody id={"information-request-author-error-body"}>{error}</MessageBarBody>
                {onRetry && (
                    <MessageBarActions id={"information-request-author-error-actions"}>
                        <Button id={"information-request-author-retry"}
                                shape={"circular"}
                                onClick={onRetry}>Try again</Button>
                    </MessageBarActions>
                )}
            </MessageBar>
        )}
        <div id={"information-request-author-status"}
             role={"status"}
             aria-live={"polite"}>
            {notice && (
                <MessageBar id={"information-request-author-notice"}
                            intent={"info"}>
                    <MessageBarBody id={"information-request-author-notice-body"}>{notice}</MessageBarBody>
                </MessageBar>
            )}
        </div>
    </>
);

export default AuthorWorkspaceMessages;
