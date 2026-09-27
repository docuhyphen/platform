import {useCallback, useState} from "react";
import {Button, MessageBar, MessageBarBody, Spinner, Text} from "@fluentui/react-components";
import {useLoadedValue} from "../../../../hooks/useLoadedValue.ts";
import {getInformationRequestOperations} from "../../../../services/informationRequestOperationsService.ts";
import {InformationRequestSlaStatus} from "../../../models/models.tsx";
import OperationsQueueFilters from "../operations-queue-filters/OperationsQueueFilters.tsx";
import OperationsQueueRow from "../operations-queue-row/OperationsQueueRow.tsx";
import {useOperationsQueueStyles} from "./OperationsQueueStyles.tsx";

const PAGE_SIZE = 25;

const OperationsQueue = () =>
{
    const styles = useOperationsQueueStyles();
    const [slaStatus, setSlaStatus] = useState<InformationRequestSlaStatus | undefined>(undefined);
    const [exceptionsOnly, setExceptionsOnly] = useState(false);
    const [offset, setOffset] = useState(0);
    const load = useCallback(
        () => getInformationRequestOperations({slaStatus, exceptionsOnly, limit: PAGE_SIZE, offset}),
        [slaStatus, exceptionsOnly, offset],
    );
    const page = useLoadedValue(load, "The operations queue could not be loaded.");

    const filterBySlaStatus = (status: InformationRequestSlaStatus | undefined) =>
    {
        setSlaStatus(status);
        setOffset(0);
    };

    const filterByExceptions = (only: boolean) =>
    {
        setExceptionsOnly(only);
        setOffset(0);
    };

    return (
        <div id={"information-request-operations-queue"}
             className={styles.queue}>
            <OperationsQueueFilters slaStatus={slaStatus}
                                    exceptionsOnly={exceptionsOnly}
                                    onSlaStatusChange={filterBySlaStatus}
                                    onExceptionsOnlyChange={filterByExceptions}/>
            {page.error && (
                <MessageBar id={"information-request-operations-error"}
                            intent={"error"}>
                    <MessageBarBody>{page.error}</MessageBarBody>
                </MessageBar>
            )}
            {!page.error && !page.value && (
                <Spinner id={"information-request-operations-loading"}
                         size={"medium"}
                         label={"Loading the operations queue"}/>
            )}
            {page.value && page.value.items.length === 0 && (
                <Text id={"information-request-operations-empty"}>No Information Requests match these filters.</Text>
            )}
            {page.value && page.value.items.length > 0 && (
                <ul id={"information-request-operations-list"}
                    className={styles.list}>
                    {page.value.items.map(row => (
                        <OperationsQueueRow key={row.requestId}
                                            row={row}/>
                    ))}
                </ul>
            )}
            {page.value && page.value.total > PAGE_SIZE && (
                <div id={"information-request-operations-pager"}
                     className={styles.pager}>
                    <Button id={"information-request-operations-previous-btn"}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={offset === 0}
                            onClick={() => setOffset(Math.max(0, offset - PAGE_SIZE))}>
                        Previous
                    </Button>
                    <Text id={"information-request-operations-range"}>
                        {`${offset + 1} to ${Math.min(offset + PAGE_SIZE, page.value.total)} of ${page.value.total}`}
                    </Text>
                    <Button id={"information-request-operations-next-btn"}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={offset + PAGE_SIZE >= page.value.total}
                            onClick={() => setOffset(offset + PAGE_SIZE)}>
                        Next
                    </Button>
                </div>
            )}
        </div>
    );
};

export default OperationsQueue;
