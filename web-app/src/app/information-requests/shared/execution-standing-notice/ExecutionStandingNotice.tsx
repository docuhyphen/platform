import {InformationRequestExecutionStandingDto} from "../../../models/models.tsx";
import {executionStandingNotice} from "../executionStandingText.ts";
import StandingNotice from "../standing-notice/StandingNotice.tsx";

interface Props
{
    idPrefix: string;
    standing: InformationRequestExecutionStandingDto;
}

const ExecutionStandingNotice = ({idPrefix, standing}: Props) => (
    <StandingNotice id={`${idPrefix}-execution-standing`}
                    notice={executionStandingNotice(standing)}/>
);

export default ExecutionStandingNotice;
