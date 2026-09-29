import {Dropdown, Field, Option, SearchBox, Switch} from "@fluentui/react-components";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {InformationRequestOperationsAssigneeDto, InformationRequestSlaStatus} from "../../../models/models.tsx";
import {slaStatusPresentation} from "../operationsLabels.ts";
import {useOperationsQueueFiltersStyles} from "./OperationsQueueFiltersStyles.tsx";

interface OperationsQueueFiltersProps
{
    query: string;
    assigneeId?: string;
    assignees: InformationRequestOperationsAssigneeDto[];
    slaStatus?: InformationRequestSlaStatus;
    exceptionsOnly: boolean;
    onQueryChange: (query: string) => void;
    onAssigneeChange: (assigneeId: string | undefined) => void;
    onSlaStatusChange: (status: InformationRequestSlaStatus | undefined) => void;
    onExceptionsOnlyChange: (exceptionsOnly: boolean) => void;
}

const ALL = "ALL";

const OperationsQueueFilters = ({
    query,
    assigneeId,
    assignees,
    slaStatus,
    exceptionsOnly,
    onQueryChange,
    onAssigneeChange,
    onSlaStatusChange,
    onExceptionsOnlyChange,
}: OperationsQueueFiltersProps) =>
{
    const styles = useOperationsQueueFiltersStyles();
    const {appUser} = useAuth();
    const choices = [
        ...(appUser?.id ? [{id: appUser.id, label: "Me"}] : []),
        ...assignees
            .filter(assignee => assignee.principalId !== appUser?.id)
            .map(assignee => ({id: assignee.principalId, label: assignee.label ?? "Unnamed party"})),
    ];
    const chosen = choices.find(choice => choice.id === assigneeId);

    return (
        <div id={"information-request-operations-filters"}
             className={styles.filters}>
            <Field id={"information-request-operations-search-field"}
                   label={"Search"}
                   hint={"A title, or the start of a request id."}
                   className={styles.search}>
                <SearchBox id={"information-request-operations-search"}
                           value={query}
                           onChange={(_, data) => onQueryChange(data.value)}/>
            </Field>
            <Field id={"information-request-operations-assignee-field"}
                   label={"Assigned to"}
                   className={styles.field}>
                <Dropdown id={"information-request-operations-assignee-filter"}
                          appearance={"outline"}
                          value={chosen?.label ?? "Anyone"}
                          selectedOptions={[assigneeId ?? ALL]}
                          onOptionSelect={(_, data) =>
                              onAssigneeChange(data.optionValue && data.optionValue !== ALL ? data.optionValue : undefined)}>
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
                          value={slaStatus ? slaStatusPresentation[slaStatus].label : "All"}
                          selectedOptions={[slaStatus ?? ALL]}
                          onOptionSelect={(_, data) =>
                              onSlaStatusChange(Object.values(InformationRequestSlaStatus).find(status => status === data.optionValue))}>
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
            <Switch id={"information-request-operations-exceptions-switch"}
                    label={"Exceptions only"}
                    checked={exceptionsOnly}
                    onChange={(_, data) => onExceptionsOnlyChange(data.checked)}/>
        </div>
    );
};

export default OperationsQueueFilters;
