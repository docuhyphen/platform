import {useState} from "react";
import {getInformationRequestSubmissionPackages} from "../../../../services/informationRequestOutcomeService.ts";
import {withdrawInformationRequestPackage} from "../../../../services/informationRequestSubmissionService.ts";
import {InformationRequestSubmissionPackageDto} from "../../../models/models.tsx";
import {informationRequestRefusalMessage} from "../../shared/informationRequestRefusal.ts";
import {useServerRead} from "../../shared/useServerRead.ts";
import {STALE_AUTHOR_NOTICE, useAuthorCommandRunner} from "../author-workspace/useAuthorCommandRunner.ts";

const PACKAGES_FAILURE = "The submissions of this request could not be loaded.";

export const SUBMISSION_WITHDRAWN = "The submission was withdrawn. Its part is open again for the responding parties.";

export interface SubmissionRecovery
{
    packages: InformationRequestSubmissionPackageDto[];
    loadError: string | null;
    reload: () => Promise<void>;
    busy: boolean;
    error: string | null;
    notice: string | null;
    withdraw: (packageId: string, reasonCode?: string) => Promise<boolean>;
}

export const useSubmissionRecovery = (requestId: string, responseETag: string, onChanged: () => void): SubmissionRecovery =>
{
    const read = useServerRead(
        requestId,
        () => getInformationRequestSubmissionPackages(requestId),
        refusal => informationRequestRefusalMessage(refusal, PACKAGES_FAILURE),
    );
    const [notice, setNotice] = useState<string | null>(null);
    const runner = useAuthorCommandRunner(requestId, async () =>
    {
        await read.reload();
        onChanged();
    });

    const withdraw = async (packageId: string, reasonCode?: string): Promise<boolean> =>
    {
        setNotice(null);
        const result = await runner.run(`withdraw-package:${packageId}:${responseETag}:${reasonCode ?? ""}`, idempotencyKey =>
            withdrawInformationRequestPackage(requestId, packageId, {reasonCode}, {expectedETag: responseETag, idempotencyKey}));
        if (result === null) return false;
        setNotice(result.outcome === "SAVED" ? SUBMISSION_WITHDRAWN : STALE_AUTHOR_NOTICE);
        return true;
    };

    return {
        packages: read.value ?? [],
        loadError: read.error,
        reload: read.reload,
        busy: runner.busy,
        error: runner.error,
        notice: notice ?? runner.notice,
        withdraw,
    };
};
