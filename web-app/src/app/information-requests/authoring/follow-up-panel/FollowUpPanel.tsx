import {useState} from "react";
import {Button, MessageBar, MessageBarBody, Text, Title3} from "@fluentui/react-components";
import {InformationRequestDto} from "../../../models/models.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import TextField from "../../shared/text-field/TextField.tsx";
import LineageList from "../lineage-list/LineageList.tsx";
import RecurrenceForm from "../recurrence-form/RecurrenceForm.tsx";
import {recurrenceSentence} from "./followUpLabels.ts";
import {useFollowUpPanelStyles} from "./FollowUpPanelStyles.tsx";
import {useFollowUps} from "./useFollowUps.ts";

interface Props
{
    request: InformationRequestDto;
    onChanged: () => void;
}

const FollowUpPanel = ({request, onChanged}: Props) =>
{
    const styles = useFollowUpPanelStyles();
    const state = useFollowUps(request, onChanged);
    const [reason, setReason] = useState("");
    const recurrence = state.lineage?.recurrence;

    return (
        <section id={"information-request-follow-up"}
                 aria-labelledby={"information-request-follow-up-title"}
                 className={styles.panel}>
            <Title3 id={"information-request-follow-up-title"}
                    as={"h2"}>
                Follow-up requests
            </Title3>
            {state.error && (
                <MessageBar id={"information-request-follow-up-error"}
                            intent={"error"}>
                    <MessageBarBody>{state.error}</MessageBarBody>
                </MessageBar>
            )}
            <Text id={"information-request-follow-up-status"}
                  role={"status"}
                  aria-live={"polite"}>
                {state.notice ?? ""}
            </Text>
            {state.lineage && <LineageList lineage={state.lineage}/>}
            {recurrence && (
                <div id={"information-request-follow-up-recurrence"}
                     className={styles.row}>
                    <div id={"information-request-follow-up-recurrence-text"}
                         className={styles.text}>
                        <Text>{recurrenceSentence(recurrence)}</Text>
                        <Text size={200}
                              className={styles.muted}>
                            {state.lineage?.nextOccurrenceDueAt
                                ? `Next request due ${formatInformationRequestTime(state.lineage.nextOccurrenceDueAt)}.`
                                : "Every scheduled request has been created."}
                        </Text>
                    </div>
                    <Button id={"information-request-follow-up-next"}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={state.busy || !state.nextOccurrenceReady}
                            onClick={() => void state.createNext(recurrence.id)}>
                        Create the next request
                    </Button>
                </div>
            )}
            {state.lineage && !recurrence && (
                <RecurrenceForm busy={state.busy}
                                onSchedule={definition => void state.schedule(definition)}/>
            )}
            <div id={"information-request-follow-up-supplement"}
                 className={styles.supplement}>
                <TextField id={"information-request-follow-up-supplement-reason"}
                           label={"Reason for the supplement"}
                           hint={"A supplement asks for more on a submitted response."}
                           value={reason}
                           className={styles.supplementReason}
                           maxLength={120}
                           onChange={setReason}/>
                <Button id={"information-request-follow-up-supplement-submit"}
                        appearance={"secondary"}
                        shape={"circular"}
                        disabled={state.busy || !reason.trim()}
                        onClick={() => void state.supplement(reason.trim())}>
                    Request a supplement
                </Button>
            </div>
        </section>
    );
};

export default FollowUpPanel;
