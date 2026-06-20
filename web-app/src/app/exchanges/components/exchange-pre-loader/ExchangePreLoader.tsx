import React from 'react';
import {Spinner} from "@fluentui/react-components";
import {usePreLandingStyles} from './ExchangePreLoadingStyles.tsx';

const ExchangePreLoader: React.FC = () =>
{
    const styles = usePreLandingStyles();

    return (
        <div className={styles.preLoadingContainer}>
            <Spinner labelPosition="after"
                     size="small"
                     label="Loading Exchanges"/>
        </div>
    );
};

export default ExchangePreLoader;