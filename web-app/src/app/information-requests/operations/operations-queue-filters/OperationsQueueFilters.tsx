import {Dropdown, Field, Option, SearchBox, Switch} from "@fluentui/react-components";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {InformationRequestSlaStatus} from "../../../models/models.tsx";
import OperationsExceptionFilter from "../operations-exception-filter/OperationsExceptionFilter.tsx";
import OperationsExchangeFilter from "../operations-exchange-filter/OperationsExchangeFilter.tsx";
import {OperationsQueueState} from "../operations-queue/useOperationsQueue.ts";
import OperationsStateFilter from "../operations-state-filter/OperationsStateFilter.tsx";
import {slaStatusPresentation} from "../operationsLabels.ts";
import {useOperationsQueueFiltersStyles} from "./OperationsQueueFiltersStyles.tsx";

interface OperationsQueueFiltersProps
{
    queue: OperationsQueueState;
}

const ALL = "ALL";

const OperationsQueueFilters = ({queue}: OperationsQueueFiltersProps) =>
{
    const styles = useOperationsQueueFiltersStyles();
    const {appUser} = useAuth();
    const {filter} = queue;
    const choices = [
        ...(appUser?.id ? [{id: appUser.id, label: "Me"}] : []),
        ...queue.assignees
            .filter(assignee => assignee.principalId !== appUser?.id)
            .map(assignee => ({id: assignee.principalId, label: assignee.label ?? "Unnamed party"})),
    ];
    const chosen = choices.find(choice => choice.id === filter.assigneeId);

    return (
        <div id={"information-request-operations-filters"}
             className={styles.filters}>
            <Field id={"information-request-operations-search-field"}
                   label={"Search"}
                   hint={"A title, or the start of a request id."}
                   className={styles.search}>
                <SearchBox id={"information-request-operations-search"}
                           value={queue.query}
                           onChange={(_, data) => queue.setQuery(data.value)}/>
            </Field>
            <OperationsStateFilter states={filter.states}
                                   onChange={queue.setStates}/>
            <OperationsExchangeFilter exchangeId={filter.exchangeId}
                                      onChange={queue.setExchangeId}/>
            <Field id={"information-request-operations-assignee-field"}
                   label={"Assigned to"}
                   className={styles.field}>
                <Dropdown id={"information-request-operations-assignee-filter"}
                          appearance={"outline"}
                          value={chosen?.label ?? (filter.assigneeId ? "Chosen party" : "Anyone")}
                          selectedOptions={[filter.assigneeId ?? ALL]}
                          onOptionSelect={(_, data) =>
                              queue.setAssigneeId(data.optionValue && data.optionValue !== ALL ? data.optionValue : undefined)}>
                    <Option id={"information-request-operations-assignee-anyone"}
                            value={ALL}>
                        Anyone
                    </Option>
                    {choices.map(choice => (
                        <Option key={choice.id}
                                id={`information-request-operations-assignee-${choice.id}`}
                                value={choice.id}>
                            {choice.label}
                        </Option>
                    ))}
                </Dropdown>
            </Field>
            <Field id={"information-request-operations-sla-field"}
                   label={"Service level"}
                   className={styles.field}>
                <Dropdown id={"information-request-operations-sla-filter"}
                          appearance={"outline"}
                          value={filter.slaStatus ? slaStatusPresentation[filter.slaStatus].label : "All"}
                          selectedOptions={[filter.slaStatus ?? ALL]}
                          onOptionSelect={(_, data) =>
                              queue.setSlaStatus(Object.values(InformationRequestSlaStatus).find(status => status === data.optionValue))}>
                    <Option id={"information-request-operations-sla-all"}
                            value={ALL}>
                        All
                    </Option>
                    {Object.values(InformationRequestSlaStatus).map(status => (
                        <Option key={status}
                                id={`information-request-operations-sla-${status.toLowerCase()}`}
                                value={status}>
                            {slaStatusPresentation[status].label}
                        </Option>
                    ))}
                </Dropdown>
            </Field>
            <OperationsExceptionFilter exceptions={filter.exceptions}
                                       onChange={queue.setExceptions}/>
            <Switch id={"information-request-operations-exceptions-switch"}
                    label={"Exceptions only"}
                    checked={filter.exceptionsOnly}
                    onChange={(_, data) => queue.setExceptionsOnly(data.checked)}/>
        </div>
    );
};

export default OperationsQueueFilters;
