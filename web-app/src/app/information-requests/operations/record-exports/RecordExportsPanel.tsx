import {Badge, Button, MessageBar, MessageBarBody, Spinner, Text} from "@fluentui/react-components";
import {DownloadIcon} from "../../../components/IconBundles.tsx";
import LoadedPanel from "../loaded-panel/LoadedPanel.tsx";
import {formattedTime} from "../operationsLabels.ts";
import {useRecordExports} from "./useRecordExports.ts";
import {useRecordExportsPanelStyles} from "./RecordExportsPanelStyles.tsx";

interface RecordExportsPanelProps
{
    requestId: string;
}

const RecordExportsPanel = ({requestId}: RecordExportsPanelProps) =>
{
    const styles = useRecordExportsPanelStyles();
    const records = useRecordExports(requestId);

    return (
        <div id={"information-request-record-exports"}
             className={styles.card}>
            <div id={"information-request-record-exports-heading"}
                 className={styles.heading}>
                <Text id={"information-request-record-exports-title"}
                      weight={"semibold"}>
                    Record exports
                </Text>
                <Button id={"information-request-record-exports-create-btn"}
                        appearance={"primary"}
                        shape={"circular"}
                        disabled={records.busy}
                        onClick={records.create}>
                    {records.busy && <Spinner size={"tiny"}/>}
                    Create export
                </Button>
            </div>
            {records.error && (
                <MessageBar id={"information-request-record-exports-command-error"}
                            intent={"error"}>
                    <MessageBarBody>{records.error}</MessageBarBody>
                </MessageBar>
            )}
            <LoadedPanel idPrefix={"information-request-record-exports"}
                         loaded={records.exports}
                         loadingLabel={"Loading record exports"}
                         emptyText={"No record exports have been made for this request."}
                         isEmpty={value => value.length === 0}>
                {value => (
                    <ul id={"information-request-record-exports-list"}
                        className={styles.list}>
                        {value.map(recordExport =>
                        {
                            const id = `information-request-record-export-${recordExport.id}`;
                            return (
                                <li id={id}
                                    key={recordExport.id}
                                    className={styles.entry}>
                                    <div id={`${id}-summary`}
                                         className={styles.summary}>
                                        <Text id={`${id}-requested`}>{`Made ${formattedTime(recordExport.requestedAt)}`}</Text>
                                        <Text id={`${id}-hash`}
                                              className={styles.detail}>
                                            {`${recordExport.contentHashAlgorithm} ${recordExport.contentHash.slice(0, 16)}, ${recordExport.contentLength} bytes, stored in ${recordExport.storageLocation}`}
                                        </Text>
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
                                            onClick={() => records.download(recordExport.id)}>
                                        Download
                                    </Button>
                                </li>
                            );
                        })}
                    </ul>
                )}
            </LoadedPanel>
        </div>
    );
};

export default RecordExportsPanel;
