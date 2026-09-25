import {useEffect, useState} from "react";
import {Badge, Text} from "@fluentui/react-components";
import {getInformationRequestAmendments} from "../../../../services/informationRequestSubmissionService.ts";
import {InformationRequestAmendmentDto} from "../../../models/models.tsx";
import {changeKindLabels, humanizedKey, noticeStateLabels} from "../submissionLabels.ts";
import {useInformationRequestAmendmentSummaryStyles} from "./InformationRequestAmendmentSummaryStyles.tsx";

interface Props
{
    requestId: string;
    accessLinkToken?: string;
    refreshKey: string;
}

const InformationRequestAmendmentSummary = ({requestId, accessLinkToken, refreshKey}: Props) =>
{
    const styles = useInformationRequestAmendmentSummaryStyles();
    const [amendments, setAmendments] = useState<InformationRequestAmendmentDto[]>([]);

    useEffect(() =>
    {
        getInformationRequestAmendments(requestId, accessLinkToken)
            .then(setAmendments)
            .catch(() => setAmendments([]));
    }, [accessLinkToken, requestId, refreshKey]);

    if (amendments.length === 0) return null;

    return (
        <section id={"information-request-amendments"}
                 className={styles.section}>
            <Text id={"information-request-amendments-title"}
                  weight={"semibold"}>
                What changed in this request
            </Text>
            {amendments.map(amendment => (
                <article id={`information-request-amendment-${amendment.id}`}
                         key={amendment.id}
                         className={styles.amendment}>
                    <div id={`information-request-amendment-${amendment.id}-header`}
                         className={styles.header}>
                        <Text weight={"semibold"}>{`Amendment ${amendment.amendmentNumber}`}</Text>
                        <Text className={styles.detail}>{new Date(amendment.amendedAt).toLocaleString()}</Text>
                        {amendment.notices.map(notice => (
                            <Badge id={`information-request-amendment-notice-${notice.id}`}
                                   key={notice.id}
                                   appearance={"outline"}
                                   color={"informative"}>
                                {noticeStateLabels[notice.deliveryState]}
                            </Badge>
                        ))}
                    </div>
                    <ul id={`information-request-amendment-${amendment.id}-changes`}
                        className={styles.changes}>
                        {amendment.changes.map(change => (
                            <li id={`information-request-amendment-${amendment.id}-${change.requirementKey}`}
                                key={change.requirementKey}>
                                <Text>{`${humanizedKey(change.requirementKey)}: ${changeKindLabels[change.changeKind]}`}</Text>
                                {change.reconfirmationRequired && (
                                    <Text className={styles.detail}> - confirm your answer again</Text>
                                )}
                            </li>
                        ))}
                    </ul>
                    {amendment.undisclosedChangeCount > 0 && (
                        <Text id={`information-request-amendment-${amendment.id}-undisclosed`}
                              className={styles.detail}>
                            {`${amendment.undisclosedChangeCount} other changes concern items handled by other parties.`}
                        </Text>
                    )}
                </article>
            ))}
        </section>
    );
};

export default InformationRequestAmendmentSummary;
