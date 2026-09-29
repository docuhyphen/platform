import {useState} from "react";
import {Button, MessageBar, MessageBarBody, Spinner, Text} from "@fluentui/react-components";
import OperationsQueueFilters from "../operations-queue-filters/OperationsQueueFilters.tsx";
import OperationsQueueRow from "../operations-queue-row/OperationsQueueRow.tsx";
import OperationsQueueToolbar from "../operations-queue-toolbar/OperationsQueueToolbar.tsx";
import SendRemindersDialog from "../send-reminders-dialog/SendRemindersDialog.tsx";
import {OPERATIONS_PAGE_SIZE, useOperationsQueue} from "./useOperationsQueue.ts";
import {useOperationsQueueStyles} from "./OperationsQueueStyles.tsx";
import {formatInformationRequestCount} from "../../shared/informationRequestFormatting.ts";

interface OperationsQueueProps
{
    canSendReminders: boolean;
}

const OperationsQueue = ({canSendReminders}: OperationsQueueProps) =>
{
    const styles = useOperationsQueueStyles();
    const queue = useOperationsQueue();
    const [confirming, setConfirming] = useState(false);
    const {page, filter} = queue;
    const offset = filter.offset;

    return (
        <div id={"information-request-operations-queue"}
             className={styles.queue}>
            <OperationsQueueFilters query={queue.query}
                                    assigneeId={filter.assigneeId}
                                    assignees={queue.assignees}
                                    slaStatus={filter.slaStatus}
                                    exceptionsOnly={filter.exceptionsOnly}
                                    onQueryChange={queue.setQuery}
                                    onAssigneeChange={queue.setAssigneeId}
                                    onSlaStatusChange={queue.setSlaStatus}
                                    onExceptionsOnlyChange={queue.setExceptionsOnly}/>
            <OperationsQueueToolbar selectedCount={queue.selected.size}
                                    canSendReminders={canSendReminders}
                                    busy={queue.busy}
                                    onSendReminders={() => setConfirming(true)}
                                    onExport={() => void queue.exportCsv()}/>
            {(page.error ?? queue.error) && (
                <MessageBar id={"information-request-operations-error"}
                            intent={"error"}
                            role={"alert"}>
                    <MessageBarBody>{queue.error ?? page.error}</MessageBarBody>
                </MessageBar>
            )}
            <Text id={"information-request-operations-status"}
                  role={"status"}
                  aria-live={"polite"}>
                {queue.notice ?? ""}
            </Text>
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
                    aria-label={"Information Requests"}
                    className={styles.list}>
                    {page.value.items.map(row => (
                        <OperationsQueueRow key={row.requestId}
                                            row={row}
                                            selectable={canSendReminders}
                                            selected={queue.selected.has(row.requestId)}
                                            onToggle={() => queue.toggle(row)}/>
                    ))}
                </ul>
            )}
            {page.value && page.value.total > OPERATIONS_PAGE_SIZE && (
                <div id={"information-request-operations-pager"}
                     className={styles.pager}>
                    <Button id={"information-request-operations-previous-btn"}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={offset === 0}
                            onClick={() => queue.setOffset(Math.max(0, offset - OPERATIONS_PAGE_SIZE))}>
                        Previous
                    </Button>
                    <Text id={"information-request-operations-range"}>
                        {`${formatInformationRequestCount(offset + 1)} to ${formatInformationRequestCount(Math.min(offset + OPERATIONS_PAGE_SIZE, page.value.total))} of ${formatInformationRequestCount(page.value.total)}`}
                    </Text>
                    <Button id={"information-request-operations-next-btn"}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={offset + OPERATIONS_PAGE_SIZE >= page.value.total}
                            onClick={() => queue.setOffset(offset + OPERATIONS_PAGE_SIZE)}>
                        Next
                    </Button>
                </div>
            )}
            {confirming && (
                <SendRemindersDialog requestCount={queue.selected.size}
                                     busy={queue.busy}
                                     onConfirm={() =>
                                     {
                                         setConfirming(false);
                                         void queue.sendReminders();
                                     }}
                                     onDismiss={() => setConfirming(false)}/>
            )}
        </div>
    );
};

export default OperationsQueue;
