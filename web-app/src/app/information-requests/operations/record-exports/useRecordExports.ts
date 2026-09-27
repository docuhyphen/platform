import {useCallback, useState} from "react";
import {LoadedValue, useLoadedValue} from "../../../../hooks/useLoadedValue.ts";
import {
    createInformationRequestRecordExport,
    getInformationRequestRecordExport,
    getInformationRequestRecordExports,
} from "../../../../services/informationRequestOperationsService.ts";
import {normalizeApiError} from "../../../../utils/apiErrorUtils.ts";
import {InformationRequestRecordExportDto} from "../../../models/models.tsx";

export interface RecordExports
{
    exports: LoadedValue<InformationRequestRecordExportDto[]>;
    busy: boolean;
    error: string | null;
    create: () => Promise<void>;
    download: (exportId: string) => Promise<void>;
}

const saveJsonFile = (fileName: string, content: unknown) =>
{
    const url = window.URL.createObjectURL(new Blob([JSON.stringify(content, null, 2)], {type: "application/json"}));
    const link = document.createElement("a");
    link.href = url;
    link.download = fileName;
    link.click();
    window.URL.revokeObjectURL(url);
};

export const useRecordExports = (requestId: string): RecordExports =>
{
    const load = useCallback(() => getInformationRequestRecordExports(requestId), [requestId]);
    const exports = useLoadedValue(load, "The record exports could not be loaded.");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const create = async () =>
    {
        setBusy(true);
        setError(null);
        try
        {
            await createInformationRequestRecordExport(requestId, crypto.randomUUID());
            exports.reload();
        }
        catch (caught: unknown)
        {
            setError(normalizeApiError(caught, "The record export could not be created.").message);
        }
        finally
        {
            setBusy(false);
        }
    };

    const download = async (exportId: string) =>
    {
        setError(null);
        try
        {
            const record = await getInformationRequestRecordExport(requestId, exportId);
            if (!record.verified) throw new Error("The export no longer matches its recorded hash and was not downloaded.");
            saveJsonFile(`information-request-record-${exportId}.json`, record.content);
        }
        catch (caught: unknown)
        {
            setError(caught instanceof Error ? caught.message : normalizeApiError(caught, "The record export could not be read.").message);
        }
    };

    return {exports, busy, error, create, download};
};
