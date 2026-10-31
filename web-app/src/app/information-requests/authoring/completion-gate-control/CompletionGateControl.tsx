import {Switch, Text, Title3} from "@fluentui/react-components";
import {useCompletionGateControlStyles} from "./CompletionGateControlStyles.tsx";

interface Props {
    gatesExchangeClosure: boolean;
    editable: boolean;
    busy: boolean;
    onChange: (gatesExchangeClosure: boolean) => void;
}

const CompletionGateControl = ({gatesExchangeClosure, editable, busy, onChange}: Props) => {
    const styles = useCompletionGateControlStyles();

    return (
        <section id={"information-request-completion-gate"}
                 aria-labelledby={"information-request-completion-gate-title"}
                 className={styles.panel}>
            <Title3 id={"information-request-completion-gate-title"}
                    as={"h2"}>
                Ending the Exchange
            </Title3>
            {editable
                ? (
                    <Switch id={"information-request-completion-gate-switch"}
                            label={"Must finish before the Exchange can end"}
                            checked={gatesExchangeClosure}
                            disabled={busy}
                            onChange={(_, data) => onChange(data.checked)}/>
                )
                : (
                    <Text id={"information-request-completion-gate-state"}
                          className={styles.state}>
                        {gatesExchangeClosure
                            ? "This request must finish before its Exchange can end."
                            : "Its Exchange can end without waiting for this request."}
                    </Text>
                )}
            <Text id={"information-request-completion-gate-hint"}
                  size={200}
                  className={styles.hint}>
                {gatesExchangeClosure
                    ? "The Exchange cannot end while this request is open."
                    : "Ending the Exchange while this request is open offers to cancel it."}
            </Text>
        </section>
    );
};

export default CompletionGateControl;
