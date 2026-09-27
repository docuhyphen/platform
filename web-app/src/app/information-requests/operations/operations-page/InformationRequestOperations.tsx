import {Button, Title2} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import {RecordPreservationIcon} from "../../../components/IconBundles.tsx";
import InformationRequestFeatureGate from "../../feature-gate/InformationRequestFeatureGate.tsx";
import OperationsQueue from "../operations-queue/OperationsQueue.tsx";
import {useOperationsAccess} from "../useOperationsAccess.ts";
import {useInformationRequestOperationsStyles} from "./InformationRequestOperationsStyles.tsx";

const InformationRequestOperations = () =>
{
    const styles = useInformationRequestOperationsStyles();
    const navigate = useNavigate();
    const access = useOperationsAccess();

    return (
        <section id={"information-request-operations-page"}
                 className={styles.page}>
            <div id={"information-request-operations-header"}
                 className={styles.header}>
                <Title2 id={"information-request-operations-title"}>Information Request operations</Title2>
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
            <InformationRequestFeatureGate idPrefix={"information-request-operations"}>
                <OperationsQueue/>
            </InformationRequestFeatureGate>
        </section>
    );
};

export default InformationRequestOperations;
