import {ExecutionStandingNoticeText} from "../../shared/executionStandingText.ts";
import StandingNotice from "../../shared/standing-notice/StandingNotice.tsx";

interface Props
{
    idPrefix: string;
    accepted?: boolean;
}

const NOT_ACCEPTED: ExecutionStandingNoticeText = {
    intent: "info",
    text: "The Exchange holding this request has not been accepted yet. You can read the request; answers, files and " +
        "submissions are accepted once the Exchange is accepted.",
};

const ExchangeAcceptanceNotice = ({idPrefix, accepted}: Props) =>
    accepted === false
        ? (
            <StandingNotice id={`${idPrefix}-exchange-acceptance`}
                            notice={NOT_ACCEPTED}/>
        )
        : null;

export default ExchangeAcceptanceNotice;
