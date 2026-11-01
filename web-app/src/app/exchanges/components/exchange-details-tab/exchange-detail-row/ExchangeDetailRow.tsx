import React from "react";
import {Caption1} from "@fluentui/react-components";
import {useExchangeDetailRowStyles} from "./ExchangeDetailRowStyles.tsx";

interface ExchangeDetailRowProps
{
    label: string;
    value: React.ReactNode;
}

const ExchangeDetailRow: React.FC<ExchangeDetailRowProps> = ({label, value}) =>
{
    const styles = useExchangeDetailRowStyles();

    return (
        <div id={`exchange-details-tab-div-1-${label}`}
             className={styles.labelValueRow}>
            <Caption1 id={"exchange-details-tab-caption1-1"}
                      className={styles.label}>{label}</Caption1>
            <Caption1 id={"exchange-details-tab-caption1-2"}>{value}</Caption1>
        </div>
    );
};

export default ExchangeDetailRow;
