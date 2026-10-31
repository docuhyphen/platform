import {Button, Text} from "@fluentui/react-components";
import {DownloadIcon} from "../../../components/IconBundles.tsx";
import {InformationRequestPrivacyRequestDto} from "../../../models/models.tsx";
import {humanizedKey} from "../../submission/submissionLabels.ts";
import {privacyKindLabels, privacyStateLabels, targetSummary} from "../privacy/privacyLabels.ts";
import {formattedTime} from "../operationsLabels.ts";
import {usePrivacyRequestListStyles} from "./PrivacyRequestListStyles.tsx";
import {usePrivacyRecordDownload} from "./usePrivacyRecordDownload.ts";

interface PrivacyRequestListProps
{
    requests: InformationRequestPrivacyRequestDto[];
    labelOf: (subjectId: string) => string;
}

const PrivacyRequestList = ({requests, labelOf}: PrivacyRequestListProps) =>
{
    const styles = usePrivacyRequestListStyles();
    const records = usePrivacyRecordDownload();

    return (
        <div id={"information-request-privacy-requests"}
             className={styles.section}>
            <Text id={"information-request-privacy-requests-title"}
                  as={"h3"}
                  size={400}
                  weight={"semibold"}
                  className={styles.heading}>
                Privacy requests
            </Text>
            {requests.length === 0 && (
                <Text id={"information-request-privacy-requests-empty"}>No privacy request has been recorded.</Text>
            )}
            {records.error && (
                <Text id={"information-request-privacy-record-error"}
                      role={"alert"}
                      className={styles.error}>
                    {records.error}
                </Text>
            )}
            {requests.length > 0 && (
                <ul id={"information-request-privacy-requests-list"}
                    aria-labelledby={"information-request-privacy-requests-title"}
                    className={styles.list}>
                    {requests.map(request =>
                    {
                        const id = `information-request-privacy-request-${request.id}`;
                        const summary = targetSummary(request);
                        return (
                            <li id={id}
                                key={request.id}
                                className={styles.row}>
                                <div id={`${id}-text`}
                                     className={styles.text}>
                                    <Text id={`${id}-label`}
                                          weight={"semibold"}>
                                        {`${privacyKindLabels[request.requestKind]} for ${labelOf(request.subjectIdentityRefId)}`}
                                    </Text>
                                    <Text id={`${id}-state`}
                                          size={200}
                                          className={styles.muted}>
                                        {`${privacyStateLabels[request.state]}. Recorded ${formattedTime(request.recordedAt)} for ${humanizedKey(request.purposeKey)}, basis ${humanizedKey(request.policyBasisKey)}.`}
                                    </Text>
                                    {request.refusalDetail && (
                                        <Text id={`${id}-refusal`}
                                              size={200}>
                                            {request.refusalDetail}
                                        </Text>
                                    )}
                                    {summary && (
                                        <Text id={`${id}-targets`}
                                              size={200}>
                                            {summary}
                                        </Text>
                                    )}
                                </div>
                                {request.recordExportId && !request.recordExportAvailable && (
                                    <Text id={`${id}-record-disposed`}
                                          size={200}>
                                        Its record was disposed with a request it covered.
                                    </Text>
                                )}
                                {request.recordExportId && request.recordExportAvailable && (
                                    <Button id={`${id}-download-record`}
                                            appearance={"secondary"}
                                            shape={"circular"}
                                            size={"small"}
                                            icon={<DownloadIcon/>}
                                            disabled={records.downloading === request.id}
                                            onClick={() => records.download(request.id)}>
                                        Download record
                                    </Button>
                                )}
                            </li>
                        );
                    })}
                </ul>
            )}
        </div>
    );
};

export default PrivacyRequestList;
