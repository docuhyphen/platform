import {useState} from "react";
import {Text} from "@fluentui/react-components";
import {InformationRequestAcceptedFactDto} from "../../../models/models.tsx";
import AcceptedFactRow from "../accepted-fact-row/AcceptedFactRow.tsx";
import PromotableAnswerRow from "../promotable-answer-row/PromotableAnswerRow.tsx";
import PromoteFactDialog from "../promote-fact-dialog/PromoteFactDialog.tsx";
import {PromotableAnswer, promotableAnswers, supportingEvidenceChoices} from "../request-outcomes-panel/outcomeLabels.ts";
import {RequestOutcomes} from "../request-outcomes-panel/useRequestOutcomes.ts";
import RevokeFactDialog from "../revoke-fact-dialog/RevokeFactDialog.tsx";
import {useAcceptedFactsSectionStyles} from "./AcceptedFactsSectionStyles.tsx";

interface Props
{
    outcomes: RequestOutcomes;
    labelOf: (requirementId: string, requirementKey?: string) => string;
    editable: boolean;
}

const AcceptedFactsSection = ({outcomes, labelOf, editable}: Props) =>
{
    const styles = useAcceptedFactsSectionStyles();
    const [promoting, setPromoting] = useState<PromotableAnswer | null>(null);
    const [revoking, setRevoking] = useState<InformationRequestAcceptedFactDto | null>(null);
    const answers = promotableAnswers(outcomes.packages);

    return (
        <section id={"information-request-accepted-facts"}
                 aria-labelledby={"information-request-accepted-facts-title"}
                 className={styles.section}>
            <Text id={"information-request-accepted-facts-title"}
                  as={"h3"}
                  size={400}
                  weight={"semibold"}
                  className={styles.heading}>
                Accepted facts
            </Text>
            <Text id={"information-request-accepted-facts-explanation"}
                  className={styles.muted}>
                An accepted fact is a submitted answer kept for reuse. Later requests about the same subject offer it
                for the respondent to confirm again. An answer in a package that needs review can be promoted once
                its review accepts it.
            </Text>
            {editable && (
                <>
                    <Text id={"information-request-promotable-answers-title"}
                          as={"h4"}
                          weight={"semibold"}
                          className={styles.heading}>
                        Answers you can promote
                    </Text>
                    {answers.length === 0 && (
                        <Text id={"information-request-promotable-answers-empty"}>
                            No submitted answer holds a value to promote.
                        </Text>
                    )}
                    {answers.length > 0 && (
                        <ul id={"information-request-promotable-answers"}
                            aria-labelledby={"information-request-promotable-answers-title"}
                            className={styles.list}>
                            {answers.map(answer => (
                                <PromotableAnswerRow key={`${answer.packageId}-${answer.item.id}`}
                                                     answer={answer}
                                                     label={labelOf(answer.item.requirementId, answer.item.requirementKey)}
                                                     busy={outcomes.busy}
                                                     onPromote={() => setPromoting(answer)}/>
                            ))}
                        </ul>
                    )}
                </>
            )}
            <Text id={"information-request-promoted-facts-title"}
                  as={"h4"}
                  weight={"semibold"}
                  className={styles.heading}>
                Promoted facts
            </Text>
            {outcomes.facts.length === 0 && (
                <Text id={"information-request-promoted-facts-empty"}>
                    No answer has been promoted yet.
                </Text>
            )}
            {outcomes.facts.length > 0 && (
                <ul id={"information-request-promoted-facts"}
                    aria-labelledby={"information-request-promoted-facts-title"}
                    className={styles.list}>
                    {outcomes.facts.map(fact => (
                        <AcceptedFactRow key={fact.id}
                                         fact={fact}
                                         label={labelOf(fact.sourceRequirementId)}
                                         editable={editable}
                                         busy={outcomes.busy}
                                         onRevoke={() => setRevoking(fact)}/>
                    ))}
                </ul>
            )}
            {promoting && (
                <PromoteFactDialog label={labelOf(promoting.item.requirementId, promoting.item.requirementKey)}
                                   evidenceChoices={supportingEvidenceChoices(outcomes.packages, promoting, labelOf)}
                                   busy={outcomes.busy}
                                   onConfirm={terms =>
                                   {
                                       setPromoting(null);
                                       void outcomes.promote({packageId: promoting.packageId, submissionItemId: promoting.item.id, ...terms});
                                   }}
                                   onDismiss={() => setPromoting(null)}/>
            )}
            {revoking && (
                <RevokeFactDialog busy={outcomes.busy}
                                  onConfirm={request =>
                                  {
                                      setRevoking(null);
                                      void outcomes.revoke(revoking, request);
                                  }}
                                  onDismiss={() => setRevoking(null)}/>
            )}
        </section>
    );
};

export default AcceptedFactsSection;
