import {Button, MessageBar, MessageBarActions, MessageBarBody} from "@fluentui/react-components";

interface Props
{
    busy: boolean;
    onNameSelf: () => void;
}

const DecisionMakerNotice = ({busy, onNameSelf}: Props) => (
    <MessageBar id={"information-request-decision-maker-notice"}
                intent={"warning"}>
        <MessageBarBody>Name a Decision Maker before issuing this request.</MessageBarBody>
        <MessageBarActions>
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
