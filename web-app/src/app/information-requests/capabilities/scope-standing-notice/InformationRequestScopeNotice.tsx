import StandingNotice from "../../shared/standing-notice/StandingNotice.tsx";
import {scopeStandingNotice} from "../scopeStandingText.ts";
import {useInformationRequestCapabilities} from "../useInformationRequestCapabilities.ts";

interface Props
{
    idPrefix: string;
}

const InformationRequestScopeNotice = ({idPrefix}: Props) =>
{
    const capabilities = useInformationRequestCapabilities();

    return (
        <StandingNotice id={`${idPrefix}-scope-standing`}
                        notice={capabilities ? scopeStandingNotice(capabilities) : null}/>
    );
};

export default InformationRequestScopeNotice;
