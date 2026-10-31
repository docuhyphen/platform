import {Text} from "@fluentui/react-components";
import {
    InformationRequestConditionEvaluationDto,
    InformationRequestConditionEvaluationState,
    InformationRequestResponseDto,
    InformationRequestTemplateRequirementDto,
} from "../../../models/models.tsx";
import {requirementElementKey} from "../../shared/responseElementKeys.ts";
import {conditionStateOf, isRequirementActive} from "../structuredResponseWorkspaceState.ts";
import {
    useStructuredResponseInactiveConditionNoticesStyles
} from "./StructuredResponseInactiveConditionNoticesStyles.tsx";

interface Props {
    occurrencePath: string;
    requirements: InformationRequestTemplateRequirementDto[];
    conditionByScope: Map<string, InformationRequestConditionEvaluationDto>;
    responses: InformationRequestResponseDto[];
}

const StructuredResponseInactiveConditionNotices = ({
                                                        occurrencePath,
                                                        requirements,
                                                        conditionByScope,
                                                        responses,
                                                    }: Props) => {
    const styles = useStructuredResponseInactiveConditionNoticesStyles();

    return (
        <>
            {requirements
                .filter(requirement => Boolean(requirement.conditionalRuleKey))
                .filter(requirement => !isRequirementActive(requirement, occurrencePath, conditionByScope, responses))
                .map(requirement => (
                    <Text
                        id={`information-request-inactive-condition-${requirementElementKey(occurrencePath, requirement.requirementKey)}`}
                        key={`${occurrencePath}-${requirement.id}`}
                        className={styles.notice}>
                        {conditionStateOf(requirement, occurrencePath, conditionByScope) === InformationRequestConditionEvaluationState.UNKNOWN
                            ? `"${requirement.prompt}" is asked once the answers it depends on are given.`
                            : `"${requirement.prompt}" is not asked because of an earlier answer.`}
                    </Text>
                ))}
        </>
    );
};

export default StructuredResponseInactiveConditionNotices;
