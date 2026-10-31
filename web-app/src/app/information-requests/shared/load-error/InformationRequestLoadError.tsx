import {Button, MessageBar, MessageBarActions, MessageBarBody} from "@fluentui/react-components";

interface Props {
    id: string;
    message: string;
    onRetry: () => void;
}

const InformationRequestLoadError = ({id, message, onRetry}: Props) => (
    <MessageBar id={id}
                intent={"error"}
                role={"alert"}>
        <MessageBarBody id={`${id}-body`}>{message}</MessageBarBody>
        <MessageBarActions id={`${id}-actions`}>
            <Button id={`${id}-retry`}
                    appearance={"secondary"}
                    shape={"circular"}
                    size={"small"}
                    onClick={onRetry}>
                Try again
            </Button>
        </MessageBarActions>
    </MessageBar>
);

export default InformationRequestLoadError;
