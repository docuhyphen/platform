import {MessageBar, MessageBarBody} from "@fluentui/react-components";
import {ReviewMessage} from "../useReviewFeedback.ts";

interface Props {
    id: string;
    message?: ReviewMessage | null;
}

const ReviewDialogFeedback = ({id, message}: Props) => message && message.intent !== "success" ? (
    <MessageBar id={id}
                key={message.text}
                intent={message.intent}>
        <MessageBarBody id={`${id}-body`}>{message.text}</MessageBarBody>
    </MessageBar>
) : null;

export default ReviewDialogFeedback;