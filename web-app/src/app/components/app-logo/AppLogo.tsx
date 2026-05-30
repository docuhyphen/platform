import React from 'react';
import {tokens} from "@fluentui/react-components";
import {useAppLogoStyles} from "./AppLogoStyles.tsx";

const AppLogo: React.FC = () =>
{
    const styles = useAppLogoStyles();

    return (
        <span className={styles.appLogoWrapper} aria-label="Docu Hyphen Logo" role="img">
            <svg
                className={styles.appLogo}
                viewBox="0 0 140 80"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
                focusable="false"
            >
                {/* Background rounded rectangle (brand) */}
                <rect x="0" y="10" rx="8" ry="8" width="70" height="40"
                      fill={tokens.colorBrandBackground}/>

                {/* "DOCU" text on the brand background */}
                <text x="10" y="37"
                      fontFamily="Arial" fontSize="18" fontWeight="bold"
                      fill={tokens.colorNeutralForegroundOnBrand}>
                    DOCU
                </text>

                {/* Hyphen symbol */}
                <rect x="79" y="25" width="20" height="6" rx="3" ry="3"
                      fill={tokens.colorBrandBackground}/>

                {/* "HYPHEN" text — adapts to theme */}
                <text x="10" y="75"
                      fontFamily="Arial" fontSize="22" fontWeight="bold"
                      fill={tokens.colorNeutralForeground1}>
                    HYPHEN
                </text>
            </svg>
        </span>
    );
};

export default AppLogo;