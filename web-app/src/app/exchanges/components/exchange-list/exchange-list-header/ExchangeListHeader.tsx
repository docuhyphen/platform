import React from 'react';
import {mergeClasses} from "@fluentui/react-components";
import {useExchangeListHeaderStyles} from "./ExchangeListHeaderStyles.tsx";

interface ExchangeListHeaderProps
{
    id: string;
    elevated: boolean;
    children: React.ReactNode;
}

const ExchangeListHeader: React.FC<ExchangeListHeaderProps> = ({id, elevated, children}) =>
{
    const styles = useExchangeListHeaderStyles();

    return (
        <div id={id}
             className={mergeClasses(styles.header, elevated && styles.headerElevated)}>
            {children}
        </div>
    );
};

export default ExchangeListHeader;
