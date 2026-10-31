import {useCallback, useState} from "react";
import {MessageBar, MessageBarBody, Tab, TabList, Text} from "@fluentui/react-components";
import AuditHistoryPanel from "../audit-history/AuditHistoryPanel.tsx";
import ClockHistoryPanel from "../clock-history/ClockHistoryPanel.tsx";
import CommandKeysScope from "../command-keys-scope/CommandKeysScope.tsx";
import NoticeHistoryPanel from "../notice-history/NoticeHistoryPanel.tsx";
import RecordStandingPanel from "../record-standing/RecordStandingPanel.tsx";
import ExecutionStandingNotice from "../../shared/execution-standing-notice/ExecutionStandingNotice.tsx";
import {useOperationsRequestStanding} from "./useOperationsRequestStanding.ts";
import {useOperationsDetailContentStyles} from "./OperationsDetailContentStyles.tsx";

const DETAIL_TABS = ["clocks", "notices", "audit", "records"] as const;
type DetailTab = typeof DETAIL_TABS[number];

const MANAGERS_ONLY = "This request's clocks, notices, audit history, and records open only for someone who manages it: "
    + "the Exchange owner or a decision maker on the request.";

interface OperationsDetailContentProps {
    requestId: string;
}

const OperationsDetailContent = ({requestId}: OperationsDetailContentProps) => {
    const styles = useOperationsDetailContentStyles();
    const [tab, setTab] = useState<DetailTab>("clocks");
    const [forbidden, setForbidden] = useState(false);
    const reportForbidden = useCallback(() => setForbidden(true), []);
    const standing = useOperationsRequestStanding(requestId);

    return (
        <CommandKeysScope>
            {standing && (
                <ExecutionStandingNotice idPrefix={"information-request-operations-detail"}
                                         standing={standing}/>
            )}
            {forbidden && (
                <MessageBar id={"information-request-operations-detail-forbidden"}
                            intent={"warning"}>
                    <MessageBarBody id={"information-request-operations-detail-forbidden-body"}>
                        {MANAGERS_ONLY}
                    </MessageBarBody>
                </MessageBar>
            )}
            {!forbidden && (
                <div id={"information-request-operations-detail-content"}
                     className={styles.content}>
                    <TabList id={"information-request-operations-detail-tabs"}
                             className={styles.tabs}
                             selectedValue={tab}
                             onTabSelect={(_, data) => setTab(DETAIL_TABS.find(candidate => candidate === data.value) ?? "clocks")}>
                        <Tab id={"information-request-operations-detail-clocks-tab"}
                             value={"clocks"}>
                            Clocks
                        </Tab>
                        <Tab id={"information-request-operations-detail-notices-tab"}
                             value={"notices"}>
                            Notices
                        </Tab>
                        <Tab id={"information-request-operations-detail-audit-tab"}
                             value={"audit"}>
                            Audit history
                        </Tab>
                        <Tab id={"information-request-operations-detail-records-tab"}
                             value={"records"}>
                            Records
                        </Tab>
                    </TabList>
                    {tab === "clocks" && (
                        <>
                            <Text id={"information-request-operations-detail-clock-note"}
                                  className={styles.note}>
                                Clock changes are made where the request is managed: pause, resume, or extend a
                                clock there with a reason, and each change appears in this history.
                            </Text>
                            <ClockHistoryPanel requestId={requestId}
                                               onForbidden={reportForbidden}/>
                        </>
                    )}
                    {tab === "notices" && (
                        <NoticeHistoryPanel requestId={requestId}
                                            onForbidden={reportForbidden}/>
                    )}
                    {tab === "audit" && (
                        <AuditHistoryPanel requestId={requestId}
                                           onForbidden={reportForbidden}/>
                    )}
                    {tab === "records" && (
                        <RecordStandingPanel requestId={requestId}
                                             onForbidden={reportForbidden}/>
                    )}
                </div>
            )}
        </CommandKeysScope>
    );
};

export default OperationsDetailContent;
