import {Dropdown, Field, Option, Switch} from "@fluentui/react-components";
import {InformationRequestSlaStatus} from "../../../models/models.tsx";
import {slaStatusPresentation} from "../operationsLabels.ts";
import {useOperationsQueueFiltersStyles} from "./OperationsQueueFiltersStyles.tsx";

interface OperationsQueueFiltersProps
{
    slaStatus?: InformationRequestSlaStatus;
    exceptionsOnly: boolean;
    onSlaStatusChange: (status: InformationRequestSlaStatus | undefined) => void;
    onExceptionsOnlyChange: (exceptionsOnly: boolean) => void;
}

const ALL_STATUSES = "ALL";

const OperationsQueueFilters = ({slaStatus, exceptionsOnly, onSlaStatusChange, onExceptionsOnlyChange}: OperationsQueueFiltersProps) =>
{
    const styles = useOperationsQueueFiltersStyles();

    return (
        <div id={"information-request-operations-filters"}
             className={styles.filters}>
            <Field id={"information-request-operations-sla-field"}
                   label={"Service level"}
                   className={styles.field}>
                <Dropdown id={"information-request-operations-sla-filter"}
                          appearance={"outline"}
                          value={slaStatus ? slaStatusPresentation[slaStatus].label : "All"}
                          selectedOptions={[slaStatus ?? ALL_STATUSES]}
                          onOptionSelect={(_, data) =>
                              onSlaStatusChange(Object.values(InformationRequestSlaStatus).find(status => status === data.optionValue))}>
                    <Option id={"information-request-operations-sla-all"}
                            value={ALL_STATUSES}>
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
