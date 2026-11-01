import React from 'react';
import {Spinner} from "@fluentui/react-components";
import {usePreLandingStyles} from './ExchangePreLoadingStyles.tsx';

const ExchangePreLoader: React.FC = () =>
{
    const styles = usePreLandingStyles();

    return (
        <div id={"exchange-pre-loader-div-1"}
             className={styles.preLoadingContainer}>
            <Spinner id={"exchange-pre-loader-spinner-1"}
                     labelPosition="after"
                     size="small"
                     label="Loading Exchanges"/>
        </div>
    );
};

export default ExchangePreLoader;