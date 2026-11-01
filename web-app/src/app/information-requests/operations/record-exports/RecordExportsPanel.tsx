import {useState} from "react";
import {Button, Field, Input, MessageBar, MessageBarBody, Spinner, Text} from "@fluentui/react-components";
import LoadedPanel from "../loaded-panel/LoadedPanel.tsx";
import RecordExportRow from "../record-export-row/RecordExportRow.tsx";
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
    const [region, setRegion] = useState("");

    const create = async () => {
        if (await records.create(region)) setRegion("");
    };

    return (
        <div id={"information-request-record-exports"}
             className={styles.card}>
            <div id={"information-request-record-exports-heading"}
                 className={styles.heading}>
                <Text id={"information-request-record-exports-title"}
                      weight={"semibold"}>
                    Record exports
                </Text>
                <div id={"information-request-record-exports-create"}
                     className={styles.create}>
                    <Field id={"information-request-record-exports-region-field"}
                           label={"Transfer region (optional)"}
                           className={styles.region}>
                        <Input id={"information-request-record-exports-region"}
                               value={region}
                               maxLength={64}
                               disabled={records.busy}
                               onChange={(_, data) => setRegion(data.value)}/>
                    </Field>
                    <Button id={"information-request-record-exports-create-btn"}
                            appearance={"primary"}
                            shape={"circular"}
                            disabled={records.busy}
                            onClick={() => { void create(); }}>
                        {records.busy && (
                            <Spinner id={"information-request-record-exports-create-spinner"}
                                     size={"tiny"}/>
                        )}
                        Create export
                    </Button>
                </div>
            </div>
            {records.error && (
                <MessageBar id={"information-request-record-exports-command-error"}
                            intent={"error"}
                            role={"alert"}>
                    <MessageBarBody id={"information-request-record-exports-command-error-body"}>
                        {records.error}
                    </MessageBarBody>
                </MessageBar>
            )}
            <Text id={"information-request-record-exports-status"}
                  role={"status"}
                  aria-live={"polite"}>
                {records.notice ?? ""}
            </Text>
            <LoadedPanel idPrefix={"information-request-record-exports"}
                         loaded={records.exports}
                         loadingLabel={"Loading record exports"}
                         emptyText={"No record exports have been made for this request."}
                         isEmpty={value => value.length === 0}>
                {value => (
                    <>
                        <ul id={"information-request-record-exports-list"}
                            className={styles.list}>
                            {value.map(recordExport => (
                                <RecordExportRow key={recordExport.id}
                                                 recordExport={recordExport}
                                                 onDownload={() => void records.download(recordExport.id)}/>
                            ))}
                        </ul>
                        <Text id={"information-request-record-exports-hash-note"}
                              className={styles.note}>
                            Each hash covers the record as it is stored. A downloaded file is laid out for reading, so
                            its own checksum differs from the hash shown.
                        </Text>
                    </>
                )}
            </LoadedPanel>
        </div>
    );
};

export default RecordExportsPanel;
