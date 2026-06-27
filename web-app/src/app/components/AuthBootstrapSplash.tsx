import React from 'react';
import {Spinner} from '@fluentui/react-components';
import {useAuthBootstrapSplashStyles} from "./AuthBootstrapSplashStyles.tsx";

/**
 * Full-viewport placeholder rendered by route guards while AuthContext is
 * doing its initial cookie-based refresh probe on app mount. Without this,
 * a closed-and-reopened browser flashes /sign-in for a frame before the
 * refresh resolves and bounces the user to their real landing page.
 */
const AuthBootstrapSplash: React.FC = () =>
{
    const styles = useAuthBootstrapSplashStyles();
    return (
        <div className={styles.splashContainer}>
            <Spinner size="small"/>
        </div>
    );
};

export default AuthBootstrapSplash;
