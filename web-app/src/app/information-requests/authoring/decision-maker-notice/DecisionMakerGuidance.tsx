import {MessageBar, MessageBarBody} from "@fluentui/react-components";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {InformationRequestPartyDto, InformationRequestState} from "../../../models/models.tsx";
import {partyLabel} from "../author-workspace/useAuthorWorkspace.ts";
import DecisionMakerNotice from "./DecisionMakerNotice.tsx";
import {decisionMakerGuidance} from "./decisionMakerGuidance.ts";

interface Props {
    state: InformationRequestState;
    parties: InformationRequestPartyDto[];
    canChangeParties: boolean;
    callerMayIssue: boolean;
    busy: boolean;
    failure: string | null;
    onNameSelf: () => void;
}

const DecisionMakerGuidance = ({
                                   state,
                                   parties,
                                   canChangeParties,
                                   callerMayIssue,
                                   busy,
                                   failure,
                                   onNameSelf
                               }: Props) => {
    const {appUser} = useAuth();
    const guidance = decisionMakerGuidance(state, parties, canChangeParties, callerMayIssue, appUser?.id);

    if (guidance?.kind === "NAME_DECISION_MAKER")
        return (
            <DecisionMakerNotice busy={busy}
                                 failure={failure}
                                 onNameSelf={onNameSelf}/>
        );
    if (guidance?.kind === "ONLY_DECISION_MAKER_ISSUES")
        return (
            <MessageBar id={"information-request-decision-maker-issuer"}
                        intent={"info"}>
                <MessageBarBody id={"information-request-decision-maker-issuer-body"}>
                    {`Only the Decision Maker, ${partyLabel(guidance.decisionMaker)}, can issue this request.`}
                </MessageBarBody>
            </MessageBar>
        );
    return null;
};

export default DecisionMakerGuidance;
