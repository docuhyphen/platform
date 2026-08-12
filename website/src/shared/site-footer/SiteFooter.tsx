import {Text} from "@fluentui/react-components";
import {Link} from "react-router-dom";
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
                <div
                    id="site-footer-meta"
                    className={styles.meta}
                >
                    <Text
                        id="site-footer-copyright"
                        size={200}
                        className={styles.copyright}
                    >
                        &copy; {year} DocuHyphen. All rights reserved.
                    </Text>
                    <nav
                        id="site-footer-legal-links"
                        className={styles.legalLinks}
                        aria-label="Legal links"
                    >
                        <Link
                            id="site-footer-privacy-policy-link"
                            to="/privacy-policy"
                            className={styles.legalLink}
                        >
                            Privacy Policy
                        </Link>
                        <Link
                            id="site-footer-terms-of-service-link"
                            to="/terms-of-service"
                            className={styles.legalLink}
                        >
                            Terms of Service
                        </Link>
                    </nav>
                </div>
            </div>
        </footer>
    );
}
