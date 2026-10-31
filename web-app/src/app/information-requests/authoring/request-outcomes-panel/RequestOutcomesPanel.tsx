import {MessageBar, MessageBarBody, Spinner, Text, Title3} from "@fluentui/react-components";
import {InformationRequestPartyDto} from "../../../models/models.tsx";
import InformationRequestLoadError from "../../shared/load-error/InformationRequestLoadError.tsx";
import {humanizedKey} from "../../submission/submissionLabels.ts";
import AcceptedFactsSection from "../accepted-facts-section/AcceptedFactsSection.tsx";
import BusinessDecisionsSection from "../business-decisions-section/BusinessDecisionsSection.tsx";
import ItemCorrectionsSection from "../item-corrections-section/ItemCorrectionsSection.tsx";
import {useRecordedCorrections} from "../item-corrections-section/useRecordedCorrections.ts";
import {OutcomeActionPermissions} from "../author-workspace-content/authorActionPermissions.ts";
import {useRequestOutcomes} from "./useRequestOutcomes.ts";
import {useRequestOutcomesPanelStyles} from "./RequestOutcomesPanelStyles.tsx";

interface Props
{
    requestId: string;
    permissions: OutcomeActionPermissions;
    prompts: Record<string, string>;
    promptsByKey: Record<string, string>;
    subjects: InformationRequestPartyDto[];
    canCorrect: boolean;
}

const RequestOutcomesPanel = ({requestId, permissions, prompts, promptsByKey, subjects, canCorrect}: Props) =>
{
    const styles = useRequestOutcomesPanelStyles();
    const outcomes = useRequestOutcomes(requestId);
    const recorded = useRecordedCorrections(requestId, canCorrect ? subjects : []);
    const correctedItemIds = new Set(recorded.corrections.map(correction => correction.submissionItemId));
    const labelOf = (requirementId: string, requirementKey?: string): string =>
        prompts[requirementId]
        ?? (requirementKey ? promptsByKey[requirementKey] ?? humanizedKey(requirementKey) : "Requested item");

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
                    <MessageBarBody id={"information-request-outcomes-error-body"}>{outcomes.error}</MessageBarBody>
                </MessageBar>
            )}
            {outcomes.loadError && (
                <InformationRequestLoadError id={"information-request-outcomes-load-error"}
                                             message={outcomes.loadError}
                                             onRetry={() => void outcomes.reload()}/>
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
                                      subjects={subjects}
                                      correctedItemIds={correctedItemIds}
                                      labelOf={labelOf}
                                      promotable={permissions.promote}
                                      revocable={permissions.revoke}/>
            )}
            {outcomes.loaded && canCorrect && subjects.length > 0 && (
                <ItemCorrectionsSection outcomes={outcomes}
                                        subjects={subjects}
                                        recorded={recorded}
                                        labelOf={labelOf}/>
            )}
            {outcomes.loaded && outcomes.decisionsVisible && (
                <BusinessDecisionsSection outcomes={outcomes}
                                          editable={permissions.decide && outcomes.canManage}/>
            )}
        </section>
    );
};

export default RequestOutcomesPanel;
