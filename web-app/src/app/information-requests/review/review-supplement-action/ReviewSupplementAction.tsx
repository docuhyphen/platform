import {useState} from "react";
import {Button, MessageBar, MessageBarBody} from "@fluentui/react-components";
import {createInformationRequestSupplement} from "../../../../services/informationRequestSubmissionService.ts";
import InformationRequestSupplementDialog
    from "../../submission/supplement-dialog/InformationRequestSupplementDialog.tsx";
import {submissionErrorMessage} from "../../submission/submissionLabels.ts";
import {useStableIdempotencyKey} from "../../shared/useStableIdempotencyKey.ts";

interface ReviewSupplementActionProps
{
    requestId: string;
    requestETag: string;
    onChanged: () => void;
}

const CREATED = "A supplemental request was created as a draft for the same parties. Whoever manages this request issues it.";
const STALE = "This request changed. Reload the review before asking for more information.";

const ReviewSupplementAction = ({requestId, requestETag, onChanged}: ReviewSupplementActionProps) =>
{
    const [dialogOpen, setDialogOpen] = useState(false);
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState<{ intent: "success" | "error"; text: string } | null>(null);
    const keyed = useStableIdempotencyKey();

    const requestSupplement = async (reason: string) =>
    {
        setBusy(true);
        try
        {
            const outcome = await keyed(`supplement|${requestId}`, {reason, expectedETag: requestETag}, idempotencyKey =>
                createInformationRequestSupplement(requestId, reason, {expectedETag: requestETag, idempotencyKey}));
            setMessage(outcome.outcome === "SAVED" ? {intent: "success", text: CREATED} : {intent: "error", text: STALE});
            if (outcome.outcome === "SAVED") setDialogOpen(false);
            onChanged();
        }
        catch (caught: unknown)
        {
            setMessage({intent: "error", text: submissionErrorMessage(caught, "The supplemental request could not be created.")});
        }
        finally
        {
            setBusy(false);
        }
    };

    return (
        <>
            {message && (!dialogOpen || message.intent === "success") && (
                <MessageBar id={"information-request-review-supplement-message"}
                            intent={message.intent}>
                    <MessageBarBody id={"information-request-review-supplement-message-body"}>{message.text}</MessageBarBody>
                </MessageBar>
            )}
            <Button id={"information-request-review-request-supplement"}
                    appearance={"secondary"}
                    shape={"circular"}
                    disabled={busy}
                    onClick={() => setDialogOpen(true)}>
                Request more information
            </Button>
            {dialogOpen && (
                <InformationRequestSupplementDialog busy={busy}
                                                    error={message?.intent === "error" ? message.text : null}
                                                    onConfirm={reason => void requestSupplement(reason)}
                                                    onDismiss={() => setDialogOpen(false)}/>
            )}
        </>
    );
};

export default ReviewSupplementAction;
