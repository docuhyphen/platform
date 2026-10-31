import {Button, MessageBar, MessageBarActions, MessageBarBody, Spinner} from "@fluentui/react-components";
import {ReviewMessage} from "../useInformationRequestReview.ts";

interface Props {
    message: ReviewMessage | null;
    onRetry: () => void;
}

const ReviewLoadState = ({message, onRetry}: Props) => message ? (
    <MessageBar id={"information-request-review-load-error"}
                intent={message.intent}>
        <MessageBarBody id={"information-request-review-load-error-body"}>{message.text}</MessageBarBody>
        <MessageBarActions id={"information-request-review-load-error-actions"}>
            <Button id={"information-request-review-load-retry"}
                    appearance={"secondary"}
                    shape={"circular"}
                    size={"small"}
                    onClick={onRetry}>
                Try again
            </Button>
        </MessageBarActions>
    </MessageBar>
) : (
    <Spinner id={"information-request-review-loading"}
             size={"medium"}
             label={"Loading review"}/>
);

export default ReviewLoadState;
