import React from 'react';
import {Spinner} from '@fluentui/react-components';
import {useAuthBootstrapSplashStyles} from "./AuthBootstrapSplashStyles.tsx";

const AuthBootstrapSplash: React.FC = () =>
{
    const styles = useAuthBootstrapSplashStyles();
    return (
        <div id={"auth-bootstrap-splash-div-1"}
             className={styles.splashContainer}>
            <Spinner id={"auth-bootstrap-splash-spinner-1"}
                     size="small"/>
        </div>
    );
};

export default AuthBootstrapSplash;
