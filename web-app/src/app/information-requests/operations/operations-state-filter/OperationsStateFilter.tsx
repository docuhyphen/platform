import {Dropdown, Field, Option} from "@fluentui/react-components";
import {InformationRequestState} from "../../../models/models.tsx";
import {requestStateLabels} from "../operationsLabels.ts";
import {useOperationsStateFilterStyles} from "./OperationsStateFilterStyles.tsx";

interface OperationsStateFilterProps {
    states: InformationRequestState[];
    onChange: (states: InformationRequestState[]) => void;
}

const STATES = Object.values(InformationRequestState);

const OperationsStateFilter = ({states, onChange}: OperationsStateFilterProps) => {
    const styles = useOperationsStateFilterStyles();
    const chosen = STATES.filter(state => states.includes(state));

    return (
        <Field id={"information-request-operations-state-field"}
               label={"State"}
               className={styles.field}>
            <Dropdown id={"information-request-operations-state-filter"}
                      appearance={"outline"}
                      multiselect={true}
                      value={chosen.length > 0 ? chosen.map(state => requestStateLabels[state]).join(", ") : "All states"}
                      selectedOptions={chosen}
                      onOptionSelect={(_, data) => onChange(STATES.filter(state => data.selectedOptions.includes(state)))}>
                {STATES.map(state => (
                    <Option key={state}
                            id={`information-request-operations-state-${state.toLowerCase()}`}
                            value={state}>
                        {requestStateLabels[state]}
                    </Option>
                ))}
            </Dropdown>
        </Field>
    );
};

export default OperationsStateFilter;
