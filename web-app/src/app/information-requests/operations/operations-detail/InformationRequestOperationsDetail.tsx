import {Button, Spinner, Title2} from "@fluentui/react-components";
import {useNavigate, useParams} from "react-router-dom";
import {BackIcon} from "../../../components/IconBundles.tsx";
import InformationRequestScopeNotice from "../../capabilities/scope-standing-notice/InformationRequestScopeNotice.tsx";
import {useSignedInOwner} from "../../shared/useSignedInOwner.ts";
import OperationsDetailContent from "../operations-detail-content/OperationsDetailContent.tsx";
import {shortId} from "../operationsLabels.ts";
import {useInformationRequestOperationsDetailStyles} from "./InformationRequestOperationsDetailStyles.tsx";

const InformationRequestOperationsDetail = () =>
{
    const styles = useInformationRequestOperationsDetailStyles();
    const navigate = useNavigate();
    const {requestId} = useParams<{ requestId: string }>();
    const owner = useSignedInOwner();

    return (
        <section id={"information-request-operations-detail-page"}
                 aria-labelledby={"information-request-operations-detail-title"}
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
                <Title2 id={"information-request-operations-detail-title"}
                        as={"h1"}
                        className={styles.title}>
                    {requestId ? `Request ${shortId(requestId)}` : "Request"}
                </Title2>
                {requestId && (
                    <Button id={"information-request-operations-detail-manage-btn"}
                            appearance={"secondary"}
                            shape={"circular"}
                            onClick={() => navigate(`/information-requests/${requestId}/manage`)}>
                        Manage this request
                    </Button>
                )}
            </div>
            <InformationRequestScopeNotice idPrefix={"information-request-operations-detail"}/>
            {requestId && owner === null && (
                <Spinner id={"information-request-operations-detail-workspace-loading"}
                         size={"medium"}
                         label={"Loading your workspace"}/>
            )}
            {requestId && owner !== null && (
                <OperationsDetailContent key={`${owner}|${requestId}`}
                                         requestId={requestId}/>
            )}
        </section>
    );
};

export default InformationRequestOperationsDetail;
