import {MessageBar, MessageBarBody, Spinner, Text, Title3} from "@fluentui/react-components";
import {InformationRequestState} from "../../../models/models.tsx";
import {humanizedKey} from "../../submission/submissionLabels.ts";
import AcceptedFactsSection from "../accepted-facts-section/AcceptedFactsSection.tsx";
import BusinessDecisionsSection from "../business-decisions-section/BusinessDecisionsSection.tsx";
import ItemCorrectionsSection from "../item-corrections-section/ItemCorrectionsSection.tsx";
import {useRequestOutcomes} from "./useRequestOutcomes.ts";
import {useRequestOutcomesPanelStyles} from "./RequestOutcomesPanelStyles.tsx";

interface Props
{
    requestId: string;
    state: InformationRequestState;
    prompts: Record<string, string>;
    subjectId?: string;
    canCorrect: boolean;
}

const FACT_STATES = new Set([InformationRequestState.ISSUED, InformationRequestState.IN_PROGRESS, InformationRequestState.CLOSED]);

const RequestOutcomesPanel = ({requestId, state, prompts, subjectId, canCorrect}: Props) =>
{
    const styles = useRequestOutcomesPanelStyles();
    const outcomes = useRequestOutcomes(requestId);
    const labelOf = (requirementId: string, requirementKey?: string): string =>
        prompts[requirementId] ?? (requirementKey ? humanizedKey(requirementKey) : "Requested item");

    return (
        <section id={"information-request-outcomes"}
                 aria-labelledby={"information-request-outcomes-title"}
                 className={styles.panel}>
            <Title3 id={"information-request-outcomes-title"}
                    as={"h2"}>
                Outcomes
            </Title3>
            {outcomes.error && (
                <MessageBar id={"information-request-outcomes-error"}
                            intent={"error"}
                            role={"alert"}>
                    <MessageBarBody>{outcomes.error}</MessageBarBody>
                </MessageBar>
            )}
            <Text id={"information-request-outcomes-status"}
                  role={"status"}
                  aria-live={"polite"}>
                {outcomes.notice ?? ""}
            </Text>
            {!outcomes.loaded && (
                <Spinner id={"information-request-outcomes-loading"}
                         size={"small"}
                         label={"Loading outcomes"}/>
            )}
            {outcomes.loaded && outcomes.canManage && (
                <AcceptedFactsSection outcomes={outcomes}
                                      labelOf={labelOf}
                                      editable={FACT_STATES.has(state)}/>
            )}
            {outcomes.loaded && canCorrect && subjectId && (
                <ItemCorrectionsSection outcomes={outcomes}
                                        subjectId={subjectId}
                                        labelOf={labelOf}/>
            )}
            {outcomes.loaded && (
                <BusinessDecisionsSection outcomes={outcomes}
                                          editable={outcomes.canManage && state !== InformationRequestState.DRAFT}/>
            )}
        </section>
    );
};

export default RequestOutcomesPanel;
