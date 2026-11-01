import React from 'react';
import {Spinner} from '@fluentui/react-components';
import {useExchangeDetailsLoadingOverlayStyles} from './ExchangeDetailsLoadingOverlayStyles.tsx';

const ExchangeDetailsLoadingOverlay: React.FC = () =>
{
    const styles = useExchangeDetailsLoadingOverlayStyles();

    return (
        <div id={"exchanges-details-loading-overlay"}
             className={styles.detailsLoadingOverlay}>
            <Spinner id={"exchanges-loading-exchange-spinner"}
                     size="small"
                     label="Loading Exchange..."
                     labelPosition="after"/>
        </div>
    );
};

export default ExchangeDetailsLoadingOverlay;
