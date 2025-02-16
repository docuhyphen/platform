import React from 'react';
import { Spinner } from "@fluentui/react-components";
import { usePreLandingStyles } from './PreLandingStyles';

const PreLanding: React.FC = () => {
    const styles = usePreLandingStyles();

    return (
        <div className={styles.preLoadingContainer}>
            <Spinner labelPosition="after"
                     size="small"
                     label="Loading Sharing Sessions" />
        </div>
    );
};

export default PreLanding;