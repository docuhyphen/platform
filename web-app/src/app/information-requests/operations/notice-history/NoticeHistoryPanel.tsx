import {useCallback} from "react";
import {Badge, Text} from "@fluentui/react-components";
import {useLoadedValue} from "../../../../hooks/useLoadedValue.ts";
import {getInformationRequestNotices} from "../../../../services/informationRequestOperationsService.ts";
import {InformationRequestNoticeHistoryDto} from "../../../models/models.tsx";
import {humanizedKey, noticeStateLabels} from "../../submission/submissionLabels.ts";
import LoadedPanel from "../loaded-panel/LoadedPanel.tsx";
import {formattedTime, noticeKindLabels, noticeStateColors} from "../operationsLabels.ts";
import {useNoticeHistoryPanelStyles} from "./NoticeHistoryPanelStyles.tsx";

interface NoticeHistoryPanelProps
{
    requestId: string;
}

const recipientOf = (notice: InformationRequestNoticeHistoryDto): string =>
{
    if (notice.endpointState === "MISSING") return "No delivery address on file";
    return notice.maskedEndpoint ? `To ${notice.maskedEndpoint}` : "Not yet addressed";
};

const attemptsOf = (notice: InformationRequestNoticeHistoryDto): string =>
    notice.attempts
        .map(attempt => attempt.failureCode
            ? `Attempt ${attempt.attemptNumber} ${attempt.outcome.toLowerCase()} (${humanizedKey(attempt.failureCode)})`
            : `Attempt ${attempt.attemptNumber} ${attempt.outcome.toLowerCase()}`)
        .join(", ");

const NoticeHistoryPanel = ({requestId}: NoticeHistoryPanelProps) =>
{
    const styles = useNoticeHistoryPanelStyles();
    const load = useCallback(() => getInformationRequestNotices(requestId), [requestId]);
    const notices = useLoadedValue(load, "The notice history could not be loaded.");

    return (
        <LoadedPanel idPrefix={"information-request-notices"}
                     loaded={notices}
                     loadingLabel={"Loading notices"}
                     emptyText={"No notices are owed for this request."}
                     isEmpty={value => value.length === 0}>
            {value => (
                <ul id={"information-request-notices"}
                    className={styles.list}>
                    {value.map(notice =>
                    {
                        const id = `information-request-notice-${notice.noticeIntentId}`;
                        return (
                            <li id={id}
                                key={notice.noticeIntentId}
                                className={styles.notice}>
                                <div id={`${id}-heading`}
                                     className={styles.heading}>
                                    <Text id={`${id}-kind`}
                                          weight={"semibold"}>
                                        {noticeKindLabels[notice.noticeKind]}
                                    </Text>
                                    <Badge id={`${id}-state`}
                                           appearance={"outline"}
                                           color={noticeStateColors[notice.deliveryState]}>
                                        {noticeStateLabels[notice.deliveryState]}
                                    </Badge>
                                </div>
                                <Text id={`${id}-recipient`}
                                      className={styles.detail}>
                                    {`${recipientOf(notice)}, owed ${formattedTime(notice.owedAt)}`}
                                </Text>
                                {notice.renderedSubject && (
                                    <Text id={`${id}-subject`}
                                          className={styles.detail}>
                                        {`Subject: ${notice.renderedSubject}`}
                                    </Text>
                                )}
                                {notice.attempts.length > 0 && (
                                    <Text id={`${id}-attempts`}
                                          className={styles.detail}>
                                        {attemptsOf(notice)}
                                    </Text>
                                )}
                            </li>
                        );
                    })}
                </ul>
            )}
        </LoadedPanel>
    );
};

export default NoticeHistoryPanel;
