import {Badge, Button, Text} from "@fluentui/react-components";
import {DownloadIcon} from "../../../components/IconBundles.tsx";
import {InformationRequestRecordExportDto} from "../../../models/models.tsx";
import {formatInformationRequestSize} from "../../shared/informationRequestFormatting.ts";
import {formattedTime} from "../operationsLabels.ts";
import {useRecordExportRowStyles} from "./RecordExportRowStyles.tsx";

interface RecordExportRowProps {
    recordExport: InformationRequestRecordExportDto;
    onDownload: () => void;
}

const transferText = (recordExport: InformationRequestRecordExportDto): string => {
    if (recordExport.transferDecision === "NOT_REQUESTED") return "no transfer requested";
    return recordExport.transferRegion ? `transfer to ${recordExport.transferRegion} permitted` : "transfer permitted";
};

const storedText = (recordExport: InformationRequestRecordExportDto): string =>
    `${recordExport.contentHashAlgorithm} ${recordExport.contentHash.slice(0, 16)} of the stored record, `
    + `${formatInformationRequestSize(recordExport.contentLength)}, stored in ${recordExport.storageLocation}, ${transferText(recordExport)}`;

const RecordExportRow = ({recordExport, onDownload}: RecordExportRowProps) => {
    const styles = useRecordExportRowStyles();
    const id = `information-request-record-export-${recordExport.id}`;

    return (
        <li id={id}
            className={styles.entry}>
            <div id={`${id}-summary`}
                 className={styles.summary}>
                <Text id={`${id}-requested`}>
                    {`Made ${formattedTime(recordExport.requestedAt)}`}
                </Text>
                <Text id={`${id}-hash`}
                      className={styles.detail}>
                    {storedText(recordExport)}
                </Text>
                {recordExport.containsConfidentialContent && (
                    <Text id={`${id}-confidential`}
                          className={styles.detail}>
                        Includes confidential information; opening it asks you to verify again.
                    </Text>
                )}
            </div>
            <Badge id={`${id}-verified`}
                   appearance={"outline"}
                   color={recordExport.verified ? "success" : "danger"}>
                {recordExport.verified ? "Verified" : "Hash mismatch"}
            </Badge>
            <Button id={`${id}-download`}
                    appearance={"secondary"}
                    shape={"circular"}
                    icon={<DownloadIcon/>}
                    disabled={!recordExport.verified}
                    onClick={onDownload}>
                Download
            </Button>
        </li>
    );
};

export default RecordExportRow;
