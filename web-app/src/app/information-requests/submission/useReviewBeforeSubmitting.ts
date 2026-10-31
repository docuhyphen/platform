import {useEffect, useState} from "react";
import {InformationRequestSubmissionPreviewDto} from "../../models/models.tsx";
import {PendingWorkBlocker, RespondentPendingWork} from "../shared/respondentPendingWork.ts";

interface ReviewedScope {
    submissionETag: string;
    stageKey: string | null;
}

export interface ReviewBeforeSubmitting {
    reviewing: boolean;
    stageKey: string | null;
    preparing: boolean;
    blocker: PendingWorkBlocker | null;
    open: () => void;
    confirm: (submit: () => void) => void;
    dismiss: () => void;
}

const scopeOf = (preview: InformationRequestSubmissionPreviewDto): ReviewedScope =>
    ({submissionETag: preview.submissionETag, stageKey: preview.stageKey ?? null});

const sameScope = (preview: InformationRequestSubmissionPreviewDto, scope: ReviewedScope): boolean =>
    preview.submissionETag === scope.submissionETag && (preview.stageKey ?? null) === scope.stageKey;

export const useReviewBeforeSubmitting = (
    reviewed: InformationRequestSubmissionPreviewDto | null,
    held: boolean,
    pendingWork?: RespondentPendingWork,
): ReviewBeforeSubmitting => {
    const [reviewing, setReviewing] = useState<ReviewedScope | null>(null);
    const [preparing, setPreparing] = useState(false);
    const blocker = pendingWork?.blocker ?? null;
    const unsaved = pendingWork?.unsaved ?? false;
    const settled = reviewed !== null && !held && blocker === null && !unsaved;

    useEffect(() => {
        if (!preparing) return;
        if (blocker !== null && blocker !== "saving") setPreparing(false);
        else if (settled && reviewed) {
            setPreparing(false);
            setReviewing(scopeOf(reviewed));
        }
    }, [blocker, preparing, reviewed, settled]);

    useEffect(() => {
        if (reviewing && reviewed && !sameScope(reviewed, reviewing)) setReviewing(null);
    }, [reviewed, reviewing]);

    const saveFirst = () => {
        if (!pendingWork) return;
        setReviewing(null);
        setPreparing(true);
        void pendingWork.flush().then(saved => {
            if (!saved) setPreparing(false);
        });
    };

    return {
        reviewing: reviewing !== null,
        stageKey: reviewing?.stageKey ?? null,
        preparing,
        blocker,
        open: () => {
            if (blocker !== null || preparing) return;
            if (unsaved) saveFirst();
            else if (reviewed) setReviewing(scopeOf(reviewed));
        },
        confirm: submit => {
            if (blocker !== null || preparing) return;
            if (unsaved) {
                saveFirst();
                return;
            }
            setReviewing(null);
            submit();
        },
        dismiss: () => setReviewing(null),
    };
};
