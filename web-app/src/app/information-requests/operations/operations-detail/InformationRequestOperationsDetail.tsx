import {useState} from "react";
import {Button, Tab, TabList, Title2} from "@fluentui/react-components";
import {useNavigate, useParams} from "react-router-dom";
import {BackIcon} from "../../../components/IconBundles.tsx";
import InformationRequestFeatureGate from "../../feature-gate/InformationRequestFeatureGate.tsx";
import AuditHistoryPanel from "../audit-history/AuditHistoryPanel.tsx";
import ClockHistoryPanel from "../clock-history/ClockHistoryPanel.tsx";
import NoticeHistoryPanel from "../notice-history/NoticeHistoryPanel.tsx";
import {shortId} from "../operationsLabels.ts";
import RecordStandingPanel from "../record-standing/RecordStandingPanel.tsx";
import {useInformationRequestOperationsDetailStyles} from "./InformationRequestOperationsDetailStyles.tsx";

const DETAIL_TABS = ["clocks", "notices", "audit", "records"] as const;
type DetailTab = typeof DETAIL_TABS[number];

const InformationRequestOperationsDetail = () =>
{
    const styles = useInformationRequestOperationsDetailStyles();
    const navigate = useNavigate();
    const {requestId} = useParams<{requestId: string}>();
    const [tab, setTab] = useState<DetailTab>("clocks");

    return (
        <section id={"information-request-operations-detail-page"}
                 className={styles.page}>
            <div id={"information-request-operations-detail-header"}
                 className={styles.header}>
                <Button id={"information-request-operations-detail-back-btn"}
                        appearance={"subtle"}
                        shape={"circular"}
                        icon={<BackIcon/>}
                        aria-label={"Back to the operations queue"}
                        title={"Back to the operations queue"}
                        onClick={() => navigate("/information-request-operations")}/>
                <Title2 id={"information-request-operations-detail-title"}>
                    {requestId ? `Request ${shortId(requestId)}` : "Request"}
                </Title2>
            </div>
            <InformationRequestFeatureGate idPrefix={"information-request-operations-detail"}>
                {requestId && (
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
                        {tab === "clocks" && <ClockHistoryPanel requestId={requestId}/>}
                        {tab === "notices" && <NoticeHistoryPanel requestId={requestId}/>}
                        {tab === "audit" && <AuditHistoryPanel requestId={requestId}/>}
                        {tab === "records" && <RecordStandingPanel requestId={requestId}/>}
                    </div>
                )}
            </InformationRequestFeatureGate>
        </section>
    );
};

export default InformationRequestOperationsDetail;
