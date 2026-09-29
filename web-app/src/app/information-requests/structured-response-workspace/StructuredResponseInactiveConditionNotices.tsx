import {Text} from "@fluentui/react-components";
import {
    InformationRequestConditionEvaluationDto,
    InformationRequestConditionEvaluationState,
    InformationRequestTemplateRequirementDto,
} from "../../models/models.tsx";
import {toFieldElementId} from "../../exchanges/components/exchange-fields-tab/fieldLayoutUtils.ts";
import {useInformationRequestStructuredResponseWorkspaceStyles} from "./InformationRequestStructuredResponseWorkspaceStyles.tsx";

interface Props
{
    requirements: InformationRequestTemplateRequirementDto[];
    conditionEvaluations: InformationRequestConditionEvaluationDto[];
}

const StructuredResponseInactiveConditionNotices = ({
    requirements,
    conditionEvaluations,
}: Props) =>
{
    const styles = useInformationRequestStructuredResponseWorkspaceStyles();
    const inactiveRules = [...new Set(
        requirements
            .map(requirement => requirement.conditionalRuleKey)
            .filter((ruleKey): ruleKey is string => Boolean(ruleKey)),
    )].filter(ruleKey => !conditionEvaluations.some(evaluation =>
        evaluation.ruleKey === ruleKey &&
        evaluation.state === InformationRequestConditionEvaluationState.TRUE));

    return (
        <>
            {inactiveRules.flatMap(ruleKey => requirements
                .filter(requirement => requirement.conditionalRuleKey === ruleKey)
                .map(requirement => (
                    <Text id={`information-request-inactive-condition-${toFieldElementId(ruleKey)}-${toFieldElementId(requirement.requirementKey)}`}
                          key={`${ruleKey}-${requirement.id}`}
                          className={styles.notice}>
                        {`"${requirement.prompt}" is not asked because of an earlier answer.`}
                    </Text>
                )))}
        </>
    );
};

export default StructuredResponseInactiveConditionNotices;
