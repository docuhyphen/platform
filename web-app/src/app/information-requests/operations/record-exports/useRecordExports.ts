import {useCallback, useState} from "react";
import {LoadedValue, useLoadedValue} from "../../../../hooks/useLoadedValue.ts";
import {
    createInformationRequestRecordExport,
    getInformationRequestRecordExport,
    getInformationRequestRecordExports,
} from "../../../../services/informationRequestOperationsService.ts";
import {normalizeApiError} from "../../../../utils/apiErrorUtils.ts";
import {InformationRequestRecordExportDto} from "../../../models/models.tsx";
import {useScopeGuard} from "../../shared/useScopeGuard.ts";
import {useStableIdempotencyKey} from "../../shared/useStableIdempotencyKey.ts";
import {useStillMounted} from "../../shared/useStillMounted.ts";
import {countedText} from "../operationsLabels.ts";
import {saveJsonFile} from "../recordFiles.ts";

export interface RecordExports
{
    exports: LoadedValue<InformationRequestRecordExportDto[]>;
    busy: boolean;
    error: string | null;
    notice: string | null;
    create: (transferRegion?: string) => Promise<boolean>;
    download: (exportId: string) => Promise<void>;
}

const withheldNotice = (record: InformationRequestRecordExportDto): string | null =>
    record.withheldRelatedRequestCount > 0
        ? `The saved file leaves out ${countedText(record.withheldRelatedRequestCount, "related request", "related requests")} that you cannot open.`
        : null;

export const useRecordExports = (requestId: string): RecordExports =>
{
    const load = useCallback(() => getInformationRequestRecordExports(requestId), [requestId]);
    const exports = useLoadedValue(load, "The record exports could not be loaded.");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const keyed = useStableIdempotencyKey();
    const guard = useScopeGuard(requestId);
    const stillMounted = useStillMounted();

    const create = async (transferRegion?: string) =>
    {
        const region = transferRegion?.trim() || undefined;
        setBusy(true);
        setError(null);
        setNotice(null);
        try
        {
            await keyed(`record-export|${requestId}`, region ?? null, key => region
                ? createInformationRequestRecordExport(requestId, key, region)
                : createInformationRequestRecordExport(requestId, key));
            exports.reload();
            return true;
        }
        catch (caught: unknown)
        {
            setError(normalizeApiError(caught, "The record export could not be created.").message);
            return false;
        }
        finally
        {
            setBusy(false);
        }
    };

    const download = async (exportId: string) =>
    {
        const owner = requestId;
        const stillShown = () => stillMounted() && guard.isCurrent(owner);
        setError(null);
        setNotice(null);
        try
        {
            const record = await getInformationRequestRecordExport(owner, exportId);
            if (!stillShown()) return;
            if (!record.verified) throw new Error("The export no longer matches its recorded hash and was not downloaded.");
            saveJsonFile(`information-request-record-${exportId}.json`, record.content);
            setNotice(withheldNotice(record));
        }
        catch (caught: unknown)
        {
            if (!stillShown()) return;
            setError(caught instanceof Error ? caught.message : normalizeApiError(caught, "The record export could not be read.").message);
        }
    };

    return {exports, busy, error, notice, create, download};
};
