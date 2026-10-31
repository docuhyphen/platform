import {Button, MessageBar, MessageBarActions, MessageBarBody, Text} from "@fluentui/react-components";

interface Props
{
    busy: boolean;
    failure: string | null;
    onNameSelf: () => void;
}

const DecisionMakerNotice = ({busy, failure, onNameSelf}: Props) => (
    <MessageBar id={"information-request-decision-maker-notice"}
                intent={"warning"}>
        <MessageBarBody id={"information-request-decision-maker-notice-body"}>
            {failure && (
                <Text id={"information-request-decision-maker-notice-failure"}
                      block={true}>
                    {`You were not named the Decision Maker when this request was created. ${failure}`}
                </Text>
            )}
            Name a Decision Maker before issuing this request.
        </MessageBarBody>
        <MessageBarActions id={"information-request-decision-maker-notice-actions"}>
            <Button id={"information-request-decision-maker-self"}
                    appearance={"primary"}
                    shape={"circular"}
                    size={"small"}
                    disabled={busy}
                    onClick={onNameSelf}>
                Make me the Decision Maker
            </Button>
        </MessageBarActions>
    </MessageBar>
);

export default DecisionMakerNotice;
