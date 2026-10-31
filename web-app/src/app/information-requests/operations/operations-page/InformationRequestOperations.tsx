import {Fragment, useState} from "react";
import {Button, MessageBar, MessageBarBody, Spinner, Tab, TabList, Title2} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import {RecordPreservationIcon} from "../../../components/IconBundles.tsx";
import InformationRequestScopeNotice from "../../capabilities/scope-standing-notice/InformationRequestScopeNotice.tsx";
import {useSignedInOwner} from "../../shared/useSignedInOwner.ts";
import AuditSearchPanel from "../audit-search/AuditSearchPanel.tsx";
import ClockPoliciesPanel from "../clock-policies/ClockPoliciesPanel.tsx";
import CommandKeysScope from "../command-keys-scope/CommandKeysScope.tsx";
import OperationsQueue from "../operations-queue/OperationsQueue.tsx";
import PrivacyPanel from "../privacy/PrivacyPanel.tsx";
import {useOperationsAccess} from "../useOperationsAccess.ts";
import {useInformationRequestOperationsStyles} from "./InformationRequestOperationsStyles.tsx";

const VIEWS = ["queue", "policies", "privacy", "audit"] as const;
type OperationsView = typeof VIEWS[number];

const InformationRequestOperations = () =>
{
    const styles = useInformationRequestOperationsStyles();
    const navigate = useNavigate();
    const access = useOperationsAccess();
    const owner = useSignedInOwner();
    const [view, setView] = useState<OperationsView>("queue");
    const shown = view === "privacy" && !access.canManagePrivacy ? "queue" : view;

    return (
        <section id={"information-request-operations-page"}
                 aria-labelledby={"information-request-operations-title"}
                 className={styles.page}>
            <div id={"information-request-operations-header"}
                 className={styles.header}>
                <Title2 id={"information-request-operations-title"}
                        as={"h1"}>
                    Information Request operations
                </Title2>
                {access.canReadRecords && (
                    <Button id={"information-request-operations-records-btn"}
                            appearance={"secondary"}
                            shape={"circular"}
                            icon={<RecordPreservationIcon/>}
                            onClick={() => navigate("/record-preservation")}>
                        Record preservation
                    </Button>
                )}
            </div>
            <InformationRequestScopeNotice idPrefix={"information-request-operations"}/>
            {access.canViewOperations && (
                <TabList id={"information-request-operations-views"}
                         className={styles.tabs}
                         selectedValue={shown}
                         onTabSelect={(_, data) => setView(VIEWS.find(candidate => candidate === data.value) ?? "queue")}>
                    <Tab id={"information-request-operations-queue-tab"}
                         value={"queue"}>
                        Queue
                    </Tab>
                    <Tab id={"information-request-operations-policies-tab"}
                         value={"policies"}>
                        Due date policies
                    </Tab>
                    {access.canManagePrivacy && (
                        <Tab id={"information-request-operations-privacy-tab"}
                             value={"privacy"}>
                            Privacy
                        </Tab>
                    )}
                    <Tab id={"information-request-operations-audit-tab"}
                         value={"audit"}>
                        Audit search
                    </Tab>
                </TabList>
            )}
            {owner === null && (
                <Spinner id={"information-request-operations-workspace-loading"}
                         size={"medium"}
                         label={"Loading your workspace"}/>
            )}
            {owner !== null && !access.canViewOperations && (
                <MessageBar id={"information-request-operations-forbidden"}
                            intent={"warning"}>
                    <MessageBarBody id={"information-request-operations-forbidden-body"}>
                        You do not have access to Information Request operations for this account.
                    </MessageBarBody>
                </MessageBar>
            )}
            {owner !== null && access.canViewOperations && (
                <Fragment key={owner}>
                    <CommandKeysScope>
                        {shown === "queue" && (
                            <OperationsQueue canSendReminders={access.canSendReminders}
                                             canPlaceHolds={access.canManageHolds}/>
                        )}
                        {shown === "policies" && (
                            <ClockPoliciesPanel canManage={access.canManageClockPolicies}
                                                ownerType={access.ownerType}/>
                        )}
                        {shown === "privacy" && <PrivacyPanel/>}
                        {shown === "audit" && <AuditSearchPanel/>}
                    </CommandKeysScope>
                </Fragment>
            )}
        </section>
    );
};

export default InformationRequestOperations;
