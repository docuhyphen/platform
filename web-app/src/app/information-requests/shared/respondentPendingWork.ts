import {createContext, useContext} from "react";

export interface ResponseWork {
    unsaved: boolean;
    saving: boolean;
    failed: boolean;
    stale: boolean;
}

export interface UploadWork {
    sending: boolean;
    kept: boolean;
}

export type PendingWorkBlocker = "saving" | "stale" | "failed" | "sending" | "kept";

export interface PendingWorkReporter {
    reportResponses: (work: ResponseWork, flush: () => Promise<boolean>) => void;
    reportUpload: (requirementId: string, work: UploadWork | null) => void;
    reportEvidenceChanged: () => void;
}

export interface RespondentPendingWork {
    blocker: PendingWorkBlocker | null;
    unsaved: boolean;
    evidenceRevision: number;
    flush: () => Promise<boolean>;
}

export const IDLE_RESPONSE_WORK: ResponseWork = {unsaved: false, saving: false, failed: false, stale: false};

export const pendingWorkBlocker = (responses: ResponseWork, uploads: UploadWork[]): PendingWorkBlocker | null => {
    if (responses.saving) return "saving";
    if (responses.unsaved && responses.stale) return "stale";
    if (responses.unsaved && responses.failed) return "failed";
    if (uploads.some(upload => upload.sending)) return "sending";
    if (uploads.some(upload => upload.kept)) return "kept";
    return null;
};

export const PendingWorkContext = createContext<PendingWorkReporter | null>(null);

export const usePendingWorkReporter = (): PendingWorkReporter | null => useContext(PendingWorkContext);
