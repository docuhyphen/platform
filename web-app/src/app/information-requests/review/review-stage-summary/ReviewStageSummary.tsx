import {Badge, Text} from "@fluentui/react-components";
import {
    InformationRequestReviewItemStanding,
    InformationRequestReviewStageStandingDto,
    InformationRequestReviewStageState,
} from "../../../models/models.tsx";
import {aggregationLabel, itemStandingLabels, stageStateLabels} from "../reviewLabels.ts";
import {useReviewStageSummaryStyles} from "./ReviewStageSummaryStyles.tsx";

interface Props
{
    stages: InformationRequestReviewStageStandingDto[];
}

const standingSummary = (stage: InformationRequestReviewStageStandingDto): string =>
{
    const waiting = stage.items.filter(item => item.standing !== InformationRequestReviewItemStanding.DECIDED);
    if (waiting.length === 0) return `All ${stage.items.length} items decided`;
    return [...new Set(waiting.map(item => itemStandingLabels[item.standing]))].join(", ");
};

const ReviewStageSummary = ({stages}: Props) =>
{
    const styles = useReviewStageSummaryStyles();

    return (
        <section id={"information-request-review-stages"}
                 className={styles.stages}>
            {stages.map(stage => (
                <article id={`information-request-review-stage-${stage.stageKey}`}
                         key={stage.stageKey}
                         className={styles.stage}>
                    <div id={`information-request-review-stage-${stage.stageKey}-header`}
                         className={styles.header}>
                        <Text id={`information-request-review-stage-${stage.stageKey}-title`}
                              weight={"semibold"}>
                            {`${stage.position}. ${stage.title}`}
                        </Text>
                        <Badge id={`information-request-review-stage-${stage.stageKey}-state`}
                               appearance={"outline"}
                               color={stage.state === InformationRequestReviewStageState.SETTLED ? "success" : "informative"}>
                            {stageStateLabels[stage.state]}
                        </Badge>
                    </div>
                    <Text id={`information-request-review-stage-${stage.stageKey}-rule`}
                          className={styles.detail}>
                        {aggregationLabel(stage)}
                    </Text>
                    <Text id={`information-request-review-stage-${stage.stageKey}-standing`}
                          className={styles.detail}>
                        {standingSummary(stage)}
                    </Text>
                </article>
            ))}
        </section>
    );
};

export default ReviewStageSummary;
