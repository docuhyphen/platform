import {Badge, Text} from "@fluentui/react-components";
import {getInformationRequestAmendments} from "../../../../services/informationRequestSubmissionService.ts";
import {informationRequestRefusalMessage} from "../../shared/informationRequestRefusal.ts";
import InformationRequestLoadError from "../../shared/load-error/InformationRequestLoadError.tsx";
import {useReloadWhenChanged} from "../../shared/useReloadWhenChanged.ts";
import {useServerRead} from "../../shared/useServerRead.ts";
import {changeKindLabels, humanizedKey, noticeStateLabels} from "../submissionLabels.ts";
import {useInformationRequestAmendmentSummaryStyles} from "./InformationRequestAmendmentSummaryStyles.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";

interface Props
{
    requestId: string;
    accessLinkToken?: string;
    refreshKey: string;
    prompts: Record<string, string>;
}

const InformationRequestAmendmentSummary = ({requestId, accessLinkToken, refreshKey, prompts}: Props) =>
{
    const styles = useInformationRequestAmendmentSummaryStyles();
    const read = useServerRead(
        `${requestId}|${accessLinkToken ?? ""}`,
        () => getInformationRequestAmendments(requestId, accessLinkToken),
        refusal => informationRequestRefusalMessage(refusal, "What changed in this request could not be loaded."),
    );
    const amendments = read.value ?? [];

    useReloadWhenChanged(refreshKey, read.reload);

    if (read.error)
    {
        return (
            <InformationRequestLoadError id={"information-request-amendments-load-error"}
                                         message={read.error}
                                         onRetry={() => void read.reload()}/>
        );
    }

    if (amendments.length === 0) return null;

    return (
        <section id={"information-request-amendments"}
                 aria-labelledby={"information-request-amendments-title"}
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
                        <Text id={`information-request-amendment-${amendment.id}-header-title`}
                              weight={"semibold"}>
                            {`Amendment ${amendment.amendmentNumber}`}
                        </Text>
                        <Text id={`information-request-amendment-${amendment.id}-header-amended-at`}
                              className={styles.detail}>
                            {formatInformationRequestTime(amendment.amendedAt)}
                        </Text>
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
                                <Text
                                    id={`information-request-amendment-${amendment.id}-${change.requirementKey}-change`}>
                                    {`${prompts[change.requirementKey] ?? humanizedKey(change.requirementKey)}: ${changeKindLabels[change.changeKind]}`}
                                </Text>
                                {change.reconfirmationRequired && (
                                    <Text
                                        id={`information-request-amendment-${amendment.id}-${change.requirementKey}-reconfirm`}
                                        className={styles.detail}>
                                        {" - confirm your answer again"}
                                    </Text>
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
