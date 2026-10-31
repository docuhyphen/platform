import {Dropdown, Field, Option} from "@fluentui/react-components";
import {InformationRequestOperationsException} from "../../../models/models.tsx";
import {exceptionLabels} from "../operationsLabels.ts";
import {useOperationsExceptionFilterStyles} from "./OperationsExceptionFilterStyles.tsx";

interface OperationsExceptionFilterProps {
    exceptions: InformationRequestOperationsException[];
    onChange: (exceptions: InformationRequestOperationsException[]) => void;
}

const KINDS = Object.values(InformationRequestOperationsException);

const kindLabel = (kind: InformationRequestOperationsException): string => {
    const label = exceptionLabels[kind];
    return label.charAt(0).toUpperCase() + label.slice(1);
};

const OperationsExceptionFilter = ({exceptions, onChange}: OperationsExceptionFilterProps) => {
    const styles = useOperationsExceptionFilterStyles();
    const chosen = KINDS.filter(kind => exceptions.includes(kind));

    return (
        <Field id={"information-request-operations-exception-field"}
               label={"Exception kinds"}
               className={styles.field}>
            <Dropdown id={"information-request-operations-exception-filter"}
                      appearance={"outline"}
                      multiselect={true}
                      value={chosen.length > 0 ? chosen.map(kindLabel).join(", ") : "All requests"}
                      selectedOptions={chosen}
                      onOptionSelect={(_, data) => onChange(KINDS.filter(kind => data.selectedOptions.includes(kind)))}>
                {KINDS.map(kind => (
                    <Option key={kind}
                            id={`information-request-operations-exception-${kind.toLowerCase()}`}
                            value={kind}>
                        {kindLabel(kind)}
                    </Option>
                ))}
            </Dropdown>
        </Field>
    );
};

export default OperationsExceptionFilter;
