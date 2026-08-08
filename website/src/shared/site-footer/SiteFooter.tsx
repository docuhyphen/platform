import {Text} from "@fluentui/react-components";
import AppLogo from "../../app-logo/AppLogo.tsx";
import {useSiteFooterStyles} from "./SiteFooterStyles.tsx";

export function SiteFooter()
{
    const styles = useSiteFooterStyles();
    const year = new Date().getFullYear();

    return (
        <footer
            id="site-footer"
            className={styles.footer}
        >
            <div
                id="site-footer-content"
                className={styles.inner}
            >
                <span
                    id="site-footer-logo"
                    className={styles.logo}
                >
                    <AppLogo/>
                </span>
                <Text
                    id="site-footer-copyright"
                    size={200}
                    className={styles.copyright}
                >
                    &copy; {year} DocuHyphen. All rights reserved.
                </Text>
            </div>
        </footer>
    );
}
