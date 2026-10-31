import {useCallback, useEffect, useRef, useState} from "react";
import {getInformationRequestSubmissionPreview} from "../../../services/informationRequestSubmissionService.ts";
import {InformationRequestSubmissionPreviewDto} from "../../models/models.tsx";
import {isInformationRequestAccessSessionEnded} from "../shared/informationRequestRefusal.ts";
import {Scoped, useScopeGuard, valueInScope} from "../shared/useScopeGuard.ts";
import {submissionErrorMessage} from "./submissionLabels.ts";

export interface SubmissionPreview {
    preview: InformationRequestSubmissionPreviewDto | null;
    reviewed: InformationRequestSubmissionPreviewDto | null;
    selectedStageKey: string | null;
    loading: boolean;
    error: string | null;
    reload: () => Promise<void>;
    selectStage: (stageKey: string) => Promise<void>;
}

interface PreviewRead {
    preview: InformationRequestSubmissionPreviewDto | null;
    error: string | null;
    loading: boolean;
    refreshKey: string;
}

const LOAD_FAILURE = "The submission summary could not be loaded.";

export const useSubmissionPreview = (
    requestId: string,
    refreshKey: string,
    accessLinkToken: string | undefined,
    onAccessSessionEnded: () => void,
): SubmissionPreview => {
    const scope = `${requestId}|${accessLinkToken ?? ""}`;
    const guard = useScopeGuard(scope);
    const [read, setRead] = useState<Scoped<PreviewRead> | null>(null);
    const [chosen, setChosen] = useState<Scoped<string> | null>(null);
    const chosenStage = useRef<Scoped<string> | null>(null);

    const load = useCallback(async (stageKey: string | undefined) => {
        const current = guard.beginLoad(scope);
        if (!current) return;
        setRead(previous => {
            const earlier = valueInScope(previous, scope);
            return {
                scope,
                value: {preview: earlier?.preview ?? null, error: earlier?.error ?? null, loading: true, refreshKey},
            };
        });
        try {
            const loaded = await getInformationRequestSubmissionPreview(requestId, stageKey, accessLinkToken);
            if (current()) setRead({scope, value: {preview: loaded, error: null, loading: false, refreshKey}});
        } catch (caught: unknown) {
            if (!current()) return;
            setRead({
                scope,
                value: {preview: null, error: submissionErrorMessage(caught, LOAD_FAILURE), loading: false, refreshKey},
            });
            if (isInformationRequestAccessSessionEnded(caught)) onAccessSessionEnded();
        }
    }, [accessLinkToken, guard, onAccessSessionEnded, refreshKey, requestId, scope]);

    const intendedStage = useCallback(
        (fallback?: string | null) => valueInScope(chosenStage.current, scope) ?? fallback ?? undefined,
        [scope],
    );

    useEffect(() => {
        void load(intendedStage());
    }, [intendedStage, load, refreshKey]);

    const shown = valueInScope(read, scope);
    const choice = valueInScope(chosen, scope);
    const loading = shown === null || shown.loading || shown.refreshKey !== refreshKey;
    const preview = shown?.preview ?? null;

    return {
        preview,
        reviewed: !loading && preview && (choice === null || preview.stageKey === choice) ? preview : null,
        selectedStageKey: choice ?? preview?.stageKey ?? null,
        loading,
        error: shown?.error ?? null,
        reload: () => load(intendedStage(preview?.stageKey)),
        selectStage: (stageKey: string) => {
            const selection = {scope, value: stageKey};
            chosenStage.current = selection;
            setChosen(selection);
            return load(stageKey);
        },
    };
};
