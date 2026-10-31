import {useMemo, useRef, useState} from "react";
import {
    IDLE_RESPONSE_WORK,
    pendingWorkBlocker,
    PendingWorkReporter,
    RespondentPendingWork,
    ResponseWork,
    UploadWork,
} from "../shared/respondentPendingWork.ts";

const sameResponseWork = (left: ResponseWork, right: ResponseWork): boolean =>
    left.unsaved === right.unsaved &&
    left.saving === right.saving &&
    left.failed === right.failed &&
    left.stale === right.stale;

const sameUploadWork = (left: UploadWork | undefined, right: UploadWork | null): boolean =>
    right === null
        ? left === undefined
        : left !== undefined && left.sending === right.sending && left.kept === right.kept;

const NOTHING_TO_FLUSH = () => Promise.resolve(true);

export const useRespondentPendingWork = () => {
    const [responses, setResponses] = useState<ResponseWork>(IDLE_RESPONSE_WORK);
    const [uploads, setUploads] = useState<Record<string, UploadWork>>({});
    const [evidenceRevision, setEvidenceRevision] = useState(0);
    const flushResponses = useRef<() => Promise<boolean>>(NOTHING_TO_FLUSH);

    const reporter = useMemo<PendingWorkReporter>(() => ({
        reportResponses: (work, flush) => {
            flushResponses.current = flush;
            setResponses(current => sameResponseWork(current, work) ? current : work);
        },
        reportUpload: (requirementId, work) => setUploads(current => {
            if (sameUploadWork(current[requirementId], work)) return current;
            const others = Object.fromEntries(Object.entries(current).filter(([key]) => key !== requirementId));
            return work ? {...others, [requirementId]: work} : others;
        }),
        reportEvidenceChanged: () => setEvidenceRevision(revision => revision + 1),
    }), []);

    const pending = useMemo<RespondentPendingWork>(() => ({
        blocker: pendingWorkBlocker(responses, Object.values(uploads)),
        unsaved: responses.unsaved,
        evidenceRevision,
        flush: () => flushResponses.current(),
    }), [evidenceRevision, responses, uploads]);

    return {reporter, pending};
};
