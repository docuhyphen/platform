import React from "react";
import {Text} from "@fluentui/react-components";
import {useExchangeDetailSectionStyles} from "./ExchangeDetailSectionStyles.tsx";

interface ExchangeDetailSectionProps
{
    id: string;
    titleId?: string;
    title?: string;
    children: React.ReactNode;
}

const ExchangeDetailSection: React.FC<ExchangeDetailSectionProps> = (
    {
        id,
        titleId,
        title,
        children
    }) =>
{
    const styles = useExchangeDetailSectionStyles();

    return (
        <div id={id}
             className={styles.sectionCard}>
            {title && (
                <Text id={titleId}
                      weight="semibold">{title}</Text>
            )}
            {children}
        </div>
    );
};

export default ExchangeDetailSection;
