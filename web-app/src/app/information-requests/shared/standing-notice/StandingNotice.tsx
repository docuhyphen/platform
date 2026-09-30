import {MessageBar, MessageBarBody} from "@fluentui/react-components";
import {ExecutionStandingNoticeText} from "../executionStandingText.ts";

interface Props
{
    id: string;
    notice: ExecutionStandingNoticeText | null;
}

const StandingNotice = ({id, notice}: Props) =>
{
    if (!notice) return null;

    return (
        <div id={id}
             role={"status"}
             aria-live={"polite"}>
            <MessageBar id={`${id}-message`}
                        intent={notice.intent}>
                <MessageBarBody>{notice.text}</MessageBarBody>
            </MessageBar>
        </div>
    );
};

export default StandingNotice;
