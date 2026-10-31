import {MessageBar, MessageBarBody, Text} from "@fluentui/react-components";
import InformationRequestLoadError from "../../shared/load-error/InformationRequestLoadError.tsx";
import RespondentReviewCard from "../respondent-review-card/RespondentReviewCard.tsx";
import ReviewAppealDialog from "../review-appeal-dialog/ReviewAppealDialog.tsx";
import {useInformationRequestReviewResultsStyles} from "./InformationRequestReviewResultsStyles.tsx";
import {useRespondentReviewResults} from "./useRespondentReviewResults.ts";

interface Props
{
    requestId: string;
    accessLinkToken?: string;
    refreshKey: string;
    requirementLabels: Record<string, string>;
    onChanged: () => void;
}

const InformationRequestReviewResults = ({requestId, accessLinkToken, refreshKey, requirementLabels, onChanged}: Props) =>
{
    const styles = useInformationRequestReviewResultsStyles();
    const {results, loadError, retry, appealing, setAppealing, busy, message, appeal, reply, comment} =
        useRespondentReviewResults(requestId, accessLinkToken, refreshKey, onChanged);

    if (results.length === 0 && !message && !loadError) return null;

    return (
        <section id={"information-request-review-results"}
                 aria-labelledby={"information-request-review-results-title"}
                 className={styles.section}>
            <Text id={"information-request-review-results-title"}
                  weight={"semibold"}>
                Review results
            </Text>
            {loadError && (
                <InformationRequestLoadError id={"information-request-review-results-load-error"}
                                             message={loadError}
                                             onRetry={() => void retry()}/>
            )}
            {message && !appealing && (
                <MessageBar id={"information-request-review-results-message"}
                            key={`${message.intent}|${message.text}`}
                            intent={message.intent}>
                    <MessageBarBody id={"information-request-review-results-message-body"}>
                        {message.text}
                    </MessageBarBody>
                </MessageBar>
            )}
            {[...results].reverse().map(result => (
                <RespondentReviewCard key={result.review.id}
                                      result={result}
                                      requirementLabels={requirementLabels}
                                      busy={busy}
                                      onAppeal={() => setAppealing(result)}
                                      onReply={(finding, body) => reply(result, finding, body)}
                                      onComment={(item, body) => comment(result, item, body)}/>
            ))}
            {appealing && (
                <ReviewAppealDialog busy={busy || !appealing.canAppeal}
                                    message={message}
                                    onConfirm={reason => void appeal(reason)}
                                    onDismiss={() => setAppealing(null)}/>
            )}
        </section>
    );
};

export default InformationRequestReviewResults;
