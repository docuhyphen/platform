import React from 'react';
import {tokens} from "@fluentui/react-components";
import {useAppLogoStyles} from "./AppLogoStyles.tsx";

const AppLogo: React.FC = () =>
{
    const styles = useAppLogoStyles();

    return (
        <span id={"app-logo-span-1"}
              className={styles.appLogoWrapper}
              aria-label="DocuHyphen Logo"
              role="img">
            <svg
                id={"app-logo-svg-1"}
                className={styles.appLogo}
                viewBox="0 0 140 80"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
                focusable="false"
            >
                <rect id={"app-logo-rect-1"}
                      x="0"
                      y="10"
                      rx="8"
                      ry="8"
                      width="70"
                      height="40"
                      fill={tokens.colorBrandBackground}/>

                <text id={"app-logo-text-1"}
                      x="10"
                      y="37"
                      fontFamily="Arial"
                      fontSize="18"
                      fontWeight="bold"
                      fill={tokens.colorNeutralForegroundOnBrand}>
                    DOCU
                </text>

                <rect id={"app-logo-rect-2"}
                      x="79"
                      y="25"
                      width="20"
                      height="6"
                      rx="3"
                      ry="3"
                      fill={tokens.colorBrandBackground}/>

                <text id={"app-logo-text-2"}
                      x="10"
                      y="75"
                      fontFamily="Arial"
                      fontSize="22"
                      fontWeight="bold"
                      fill={tokens.colorNeutralForeground1}>
                    HYPHEN
                </text>
            </svg>
        </span>
    );
};

export default AppLogo;