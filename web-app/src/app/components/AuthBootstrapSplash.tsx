import React from 'react';
import {Spinner} from '@fluentui/react-components';

/**
 * Full-viewport placeholder rendered by route guards while AuthContext is
 * doing its initial cookie-based refresh probe on app mount. Without this,
 * a closed-and-reopened browser flashes /sign-in for a frame before the
 * refresh resolves and bounces the user to their real landing page.
 */
const AuthBootstrapSplash: React.FC = () => (
    <div
        style={{
            position: 'fixed',
            inset: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
        }}
    >
        <Spinner size="large"/>
    </div>
);

export default AuthBootstrapSplash;
