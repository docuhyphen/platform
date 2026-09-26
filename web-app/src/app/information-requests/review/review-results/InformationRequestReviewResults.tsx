import {useCallback, useEffect, useState} from "react";
import {MessageBar, MessageBarBody, Text} from "@fluentui/react-components";
import {
    appealInformationRequestReview,
    getInformationRequestReviewResults,
} from "../../../../services/informationRequestReviewService.ts";
import {InformationRequestRespondentReviewDto} from "../../../models/models.tsx";
import {submissionErrorMessage} from "../../submission/submissionLabels.ts";
import RespondentReviewCard from "../respondent-review-card/RespondentReviewCard.tsx";
import ReviewAppealDialog from "../review-appeal-dialog/ReviewAppealDialog.tsx";
import {ReviewMessage} from "../useInformationRequestReview.ts";
import {useInformationRequestReviewResultsStyles} from "./InformationRequestReviewResultsStyles.tsx";

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
    const [results, setResults] = useState<InformationRequestRespondentReviewDto[]>([]);
    const [appealing, setAppealing] = useState<InformationRequestRespondentReviewDto | null>(null);
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState<ReviewMessage | null>(null);

    const load = useCallback(() =>
    {
        getInformationRequestReviewResults(requestId, accessLinkToken)
            .then(setResults)
            .catch(() => setResults([]));
    }, [accessLinkToken, requestId]);

    useEffect(() =>
    {
        load();
    }, [load, refreshKey]);

    const appeal = async (reason: string) =>
    {
        if (!appealing) return;
        setBusy(true);
        try
        {
            const outcome = await appealInformationRequestReview(requestId, appealing.review.id, {reason}, {
                expectedETag: appealing.review.reviewETag,
                idempotencyKey: crypto.randomUUID(),
                accessLinkToken,
            });
            setMessage(outcome.outcome === "SAVED"
                ? {intent: "success", text: "Your appeal was recorded. A new review will decide it."}
                : {intent: "warning", text: "This review changed. Reload the request before appealing."});
            setAppealing(null);
            if (outcome.outcome === "SAVED")
            {
                onChanged();
                load();
            }
        }
        catch (caught: unknown)
        {
            setMessage({intent: "error", text: submissionErrorMessage(caught, "The appeal could not be recorded.")});
        }
        finally
        {
            setBusy(false);
        }
    };

    if (results.length === 0 && !message) return null;

    return (
        <section id={"information-request-review-results"}
                 className={styles.section}>
            <Text id={"information-request-review-results-title"}
                  weight={"semibold"}>
                Review results
            </Text>
            {message && (
                <MessageBar id={"information-request-review-results-message"}
                            intent={message.intent}>
                    <MessageBarBody>{message.text}</MessageBarBody>
                </MessageBar>
            )}
            {[...results].reverse().map(result => (
                <RespondentReviewCard key={result.review.id}
                                      result={result}
                                      requirementLabels={requirementLabels}
                                      busy={busy}
                                      onAppeal={() => setAppealing(result)}/>
            ))}
            {appealing && (
                <ReviewAppealDialog busy={busy}
                                    onConfirm={reason => void appeal(reason)}
                                    onDismiss={() => setAppealing(null)}/>
            )}
        </section>
    );
};

export default InformationRequestReviewResults;
