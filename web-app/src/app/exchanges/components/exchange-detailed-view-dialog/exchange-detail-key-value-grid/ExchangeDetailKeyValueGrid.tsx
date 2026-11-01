import React from "react";
import {mergeClasses, Text} from "@fluentui/react-components";
import {useExchangeDetailKeyValueGridStyles} from "./ExchangeDetailKeyValueGridStyles.tsx";

export interface ExchangeDetailRow
{
    labelId: string;
    valueId: string;
    label: string;
    value: React.ReactNode;
}

interface ExchangeDetailKeyValueGridProps
{
    id: string;
    rows: ExchangeDetailRow[];
    className?: string;
}

const ExchangeDetailKeyValueGrid: React.FC<ExchangeDetailKeyValueGridProps> = (
    {
        id,
        rows,
        className
    }) =>
{
    const styles = useExchangeDetailKeyValueGridStyles();

    return (
        <div id={id}
             className={mergeClasses(styles.keyValueGrid, className)}>
            {rows.map((row) => (
                <React.Fragment key={row.labelId}>
                    <Text id={row.labelId}
                          className={styles.keyLabel}>{row.label}</Text>
                    <Text id={row.valueId}>{row.value}</Text>
                </React.Fragment>
            ))}
        </div>
    );
};

export default ExchangeDetailKeyValueGrid;
