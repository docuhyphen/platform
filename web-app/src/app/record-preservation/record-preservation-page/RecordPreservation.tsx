import {Button, MessageBar, MessageBarBody, Spinner, Title2} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import {BackIcon} from "../../components/IconBundles.tsx";
import InformationRequestScopeNotice
    from "../../information-requests/capabilities/scope-standing-notice/InformationRequestScopeNotice.tsx";
import CommandKeysScope from "../../information-requests/operations/command-keys-scope/CommandKeysScope.tsx";
import {useOperationsAccess} from "../../information-requests/operations/useOperationsAccess.ts";
import {useSignedInOwner} from "../../information-requests/shared/useSignedInOwner.ts";
import RecordDisposalList from "../disposal-list/RecordDisposalList.tsx";
import RecordHoldList from "../hold-list/RecordHoldList.tsx";
import RetentionScheduleCard from "../retention-schedule/RetentionScheduleCard.tsx";
import {useRecordPreservationStyles} from "./RecordPreservationStyles.tsx";

const RecordPreservation = () =>
{
    const styles = useRecordPreservationStyles();
    const navigate = useNavigate();
    const access = useOperationsAccess();
    const owner = useSignedInOwner();

    return (
        <section id={"record-preservation-page"}
                 aria-labelledby={"record-preservation-title"}
                 className={styles.page}>
            <div id={"record-preservation-header"}
                 className={styles.header}>
                <Button id={"record-preservation-back-btn"}
                        appearance={"subtle"}
                        shape={"circular"}
                        icon={<BackIcon/>}
                        aria-label={"Back to Information Request operations"}
                        title={"Back to Information Request operations"}
                        onClick={() => navigate("/information-request-operations")}/>
                <Title2 id={"record-preservation-title"}
                        as={"h1"}>
                    Record preservation
                </Title2>
            </div>
            <InformationRequestScopeNotice idPrefix={"record-preservation"}/>
            {owner === null && (
                <Spinner id={"record-preservation-workspace-loading"}
                         size={"medium"}
                         label={"Loading your workspace"}/>
            )}
            {owner !== null && access.canReadRecords && (
                <div id={"record-preservation-content"}
                     key={owner}
                     className={styles.content}>
                    <CommandKeysScope>
                        <RecordHoldList canManage={access.canManageHolds}/>
                        <RetentionScheduleCard canManage={access.canManageRetention}/>
                        <RecordDisposalList/>
                    </CommandKeysScope>
                </div>
            )}
            {owner !== null && !access.canReadRecords && (
                <MessageBar id={"record-preservation-forbidden"}
                            intent={"warning"}>
                    <MessageBarBody id={"record-preservation-forbidden-body"}>
                        You do not have access to record preservation for this account.
                    </MessageBarBody>
                </MessageBar>
            )}
        </section>
    );
};

export default RecordPreservation;
