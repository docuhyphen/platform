import {Text} from "@fluentui/react-components";
import {InformationRequestPrivacyRequestDto} from "../../../models/models.tsx";
import {humanizedKey} from "../../submission/submissionLabels.ts";
import {usePrivacyPanelStyles} from "../privacy/PrivacyPanelStyles.tsx";
import {privacyKindLabels, privacyStateLabels, targetSummary} from "../privacy/privacyLabels.ts";
import {formattedTime} from "../operationsLabels.ts";

interface PrivacyRequestListProps
{
    requests: InformationRequestPrivacyRequestDto[];
    labelOf: (subjectId: string) => string;
}

const PrivacyRequestList = ({requests, labelOf}: PrivacyRequestListProps) =>
{
    const styles = usePrivacyPanelStyles();

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
                            </li>
                        );
                    })}
                </ul>
            )}
        </div>
    );
};

export default PrivacyRequestList;
