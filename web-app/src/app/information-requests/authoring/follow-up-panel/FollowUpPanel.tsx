import {useState} from "react";
import {Button, MessageBar, MessageBarBody, Spinner, Text, Title3} from "@fluentui/react-components";
import {
    InformationRequestDto,
    InformationRequestExecutionStandingKind,
    InformationRequestTemplateSectionDto,
} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import InformationRequestLoadError from "../../shared/load-error/InformationRequestLoadError.tsx";
import LineageList from "../lineage-list/LineageList.tsx";
import RecurrenceForm from "../recurrence-form/RecurrenceForm.tsx";
import RefreshRulesPanel from "../refresh-rules-panel/RefreshRulesPanel.tsx";
import {FollowUpActionPermissions} from "../author-workspace-content/authorActionPermissions.ts";
import {SUPPLEMENT_REASON_OPTIONS} from "../requestReasonCodes.ts";
import {nextOccurrenceSentence, recurrenceSentence} from "./followUpLabels.ts";
import {useFollowUpPanelStyles} from "./FollowUpPanelStyles.tsx";
import {useFollowUps} from "./useFollowUps.ts";

interface Props
{
    request: InformationRequestDto;
    sections: InformationRequestTemplateSectionDto[];
    permissions: FollowUpActionPermissions;
    onChanged: () => void;
}

const FollowUpPanel = ({request, sections, permissions, onChanged}: Props) =>
{
    const styles = useFollowUpPanelStyles();
    const state = useFollowUps(request, onChanged);
    const [reason, setReason] = useState("");
    const recurrence = state.lineage?.recurrence ?? state.lineage?.series;
    const partOfSeries = !state.lineage?.recurrence && Boolean(state.lineage?.series);
    const continuesSeries = request.executionStanding.kind === InformationRequestExecutionStandingKind.ACTIVE
        && state.lineage?.nextOccurrenceCreatable === true;
    const nextSentence = state.lineage ? nextOccurrenceSentence(state.lineage) : null;
    const canSchedule = permissions.schedule;
    const canFollowUp = permissions.create;

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
                            intent={"error"}
                            role={"alert"}>
                    <MessageBarBody id={"information-request-follow-up-error-body"}>{state.error}</MessageBarBody>
                </MessageBar>
            )}
            <Text id={"information-request-follow-up-status"}
                  role={"status"}
                  aria-live={"polite"}>
                {state.notice ?? ""}
            </Text>
            {state.loadError && (
                <InformationRequestLoadError id={"information-request-follow-up-load-error"}
                                             message={state.loadError}
                                             onRetry={() => void state.reload()}/>
            )}
            {!state.lineage && !state.loadError && (
                <Spinner id={"information-request-follow-up-loading"}
                         size={"tiny"}
                         label={"Loading follow-up requests"}/>
            )}
            {state.lineage && <LineageList lineage={state.lineage}/>}
            {recurrence && (
                <div id={"information-request-follow-up-recurrence"}
                     className={styles.row}>
                    <div id={"information-request-follow-up-recurrence-text"}
                         className={styles.text}>
                        <Text id={"information-request-follow-up-recurrence-summary"}>
                            {partOfSeries ? `This request is part of a series. ${recurrenceSentence(recurrence)}` : recurrenceSentence(recurrence)}
                        </Text>
                        {nextSentence && (
                            <Text id={"information-request-follow-up-recurrence-next"}
                                  size={200}
                                  className={styles.muted}>
                                {nextSentence}
                            </Text>
                        )}
                    </div>
                    {continuesSeries && (
                        <Button id={"information-request-follow-up-next"}
                                appearance={"secondary"}
                                shape={"circular"}
                                disabled={state.busy || !state.nextOccurrenceReady}
                                onClick={() => void state.createNext(recurrence)}>
                            Create the next request
                        </Button>
                    )}
                </div>
            )}
            {canSchedule && state.lineage && !recurrence && (
                <RecurrenceForm busy={state.busy}
                                onSchedule={definition => void state.schedule(definition)}/>
            )}
            {state.lineage && (
                <RefreshRulesPanel rules={state.lineage.refreshRules ?? []}
                                   sections={sections}
                                   canDefine={canSchedule}
                                   canRefresh={canFollowUp}
                                   busy={state.busy}
                                   onDefine={definition => void state.defineRefreshRule(definition)}
                                   onRefresh={ruleId => void state.refresh(ruleId)}/>
            )}
            {canFollowUp && (
                <div id={"information-request-follow-up-supplement"}
                     className={styles.supplement}>
                    <ChoiceSelect id={"information-request-follow-up-supplement-reason"}
                                  label={"Reason for the supplement"}
                                  hint={"A supplement asks for more on a submitted response."}
                                  value={reason}
                                  placeholder={"Choose a reason"}
                                  options={SUPPLEMENT_REASON_OPTIONS}
                                  disabled={state.busy}
                                  className={styles.supplementReason}
                                  onChange={setReason}/>
                    <Button id={"information-request-follow-up-supplement-submit"}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={state.busy || !reason}
                            onClick={() => void state.supplement(reason).then(result => {
                                if (result !== null) setReason("");
                            })}>
                        Request a supplement
                    </Button>
                </div>
            )}
        </section>
    );
};

export default FollowUpPanel;
