import {useState} from "react";
import {createInformationRequestSupplement} from "../../../../services/informationRequestSubmissionService.ts";
import {InformationRequestDto} from "../../../models/models.tsx";
import {isInformationRequestAccessSessionEnded} from "../../shared/informationRequestRefusal.ts";
import {useStableIdempotencyKey} from "../../shared/useStableIdempotencyKey.ts";
import {submissionErrorMessage} from "../submissionLabels.ts";

export interface SupplementMessage
{
    intent: "success" | "error";
    text: string;
}

export interface SupplementRequest
{
    dialogOpen: boolean;
    busy: boolean;
    message: SupplementMessage | null;
    successorId: string | null;
    openDialog: () => void;
    closeDialog: () => void;
    request: (reason: string) => Promise<void>;
}

const CREATED = "A supplemental request was created as a draft for the same parties.";
const STALE = "This request changed. Reload it before asking for more information.";
const FAILED = "The supplemental request could not be created.";

export const useSupplementRequest = (request: InformationRequestDto, onChanged: () => void): SupplementRequest =>
{
    const [dialogOpen, setDialogOpen] = useState(false);
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState<SupplementMessage | null>(null);
    const [successorId, setSuccessorId] = useState<string | null>(null);
    const keyed = useStableIdempotencyKey();

    const requestSupplement = async (reason: string) =>
    {
        setBusy(true);
        try
        {
            const expectedETag = request.requestETag;
            const outcome = await keyed(`supplement|${request.id}`, {reason, expectedETag}, idempotencyKey =>
                createInformationRequestSupplement(request.id, reason || undefined, {expectedETag, idempotencyKey}));
            setMessage(outcome.outcome === "SAVED" ? {intent: "success", text: CREATED} : {intent: "error", text: STALE});
            if (outcome.outcome === "SAVED")
            {
                setSuccessorId(outcome.data.successor?.id ?? null);
                setDialogOpen(false);
            }
            onChanged();
        }
        catch (caught: unknown)
        {
            if (isInformationRequestAccessSessionEnded(caught)) onChanged();
            setMessage({intent: "error", text: submissionErrorMessage(caught, FAILED)});
        }
        finally
        {
            setBusy(false);
        }
    };

    return {
        dialogOpen,
        busy,
        message,
        successorId,
        openDialog: () => setDialogOpen(true),
        closeDialog: () => setDialogOpen(false),
        request: requestSupplement,
    };
};
