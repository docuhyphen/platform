import {useState} from "react";
import {
    getInformationRequestPrivacyRecordExport
} from "../../../../services/informationRequestAdministrationService.ts";
import {normalizeApiError} from "../../../../utils/apiErrorUtils.ts";
import {useStillMounted} from "../../shared/useStillMounted.ts";
import {saveJsonFile} from "../recordFiles.ts";

export interface PrivacyRecordDownload {
    error: string | null;
    downloading: string | null;
    download: (privacyRequestId: string) => Promise<void>;
}

export const usePrivacyRecordDownload = (): PrivacyRecordDownload => {
    const [error, setError] = useState<string | null>(null);
    const [downloading, setDownloading] = useState<string | null>(null);
    const stillMounted = useStillMounted();

    const download = async (privacyRequestId: string) => {
        setError(null);
        setDownloading(privacyRequestId);
        try {
            const record = await getInformationRequestPrivacyRecordExport(privacyRequestId);
            if (!stillMounted()) return;
            if (!record.verified) throw new Error("The record no longer matches its recorded hash and was not downloaded.");
            saveJsonFile(`subject-record-${record.id}.json`, record.content);
        } catch (caught: unknown) {
            if (!stillMounted()) return;
            setError(caught instanceof Error ? caught.message : normalizeApiError(caught, "The subject record could not be read.").message);
        } finally {
            setDownloading(null);
        }
    };

    return {error, downloading, download};
};
