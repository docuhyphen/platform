import {useState} from "react";
import {Combobox, Field, Option} from "@fluentui/react-components";
import {useExchangeFilterChoices} from "./useExchangeFilterChoices.ts";
import {useOperationsExchangeFilterStyles} from "./OperationsExchangeFilterStyles.tsx";

interface OperationsExchangeFilterProps {
    exchangeId?: string;
    onChange: (exchangeId: string | undefined) => void;
}

const ANY = "ANY";

const OperationsExchangeFilter = ({exchangeId, onChange}: OperationsExchangeFilterProps) => {
    const styles = useOperationsExchangeFilterStyles();
    const [text, setText] = useState("");
    const [query, setQuery] = useState("");
    const [active, setActive] = useState(false);
    const choices = useExchangeFilterChoices(query, active);

    const type = (value: string) => {
        setActive(true);
        setText(value);
        setQuery(value);
        if (!value.trim() && exchangeId) onChange(undefined);
    };

    const choose = (optionValue?: string) => {
        if (optionValue === undefined) return;
        const chosen = choices.find(choice => choice.id === optionValue);
        setText(chosen?.name ?? "");
        onChange(chosen?.id);
    };

    return (
        <Field id={"information-request-operations-exchange-field"}
               label={"Exchange"}
               className={styles.field}>
            <Combobox id={"information-request-operations-exchange-filter"}
                      appearance={"outline"}
                      freeform={true}
                      placeholder={"Any Exchange"}
                      value={text}
                      selectedOptions={[exchangeId ?? ANY]}
                      onOpenChange={(_, data) => data.open && setActive(true)}
                      onChange={event => type(event.target.value)}
                      onOptionSelect={(_, data) => choose(data.optionValue)}>
                <Option id={"information-request-operations-exchange-any"}
                        value={ANY}>
                    Any Exchange
                </Option>
                {choices.map(choice => (
                    <Option key={choice.id}
                            id={`information-request-operations-exchange-${choice.id}`}
                            value={choice.id}>
                        {choice.name}
                    </Option>
                ))}
            </Combobox>
        </Field>
    );
};

export default OperationsExchangeFilter;
