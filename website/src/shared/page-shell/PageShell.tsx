import type {ReactNode} from "react";
import {LandingHeader} from "../../landing/landing-header/LandingHeader.tsx";
import {SiteFooter} from "../site-footer/SiteFooter.tsx";
import {usePageShellStyles} from "./PageShellStyles.tsx";

type PageShellProps = {
    children: ReactNode;
    constrain?: boolean;
};

export function PageShell({children, constrain = true}: PageShellProps)
{
    const styles = usePageShellStyles();

    return (
        <div id={"page-shell"}
             className={styles.page}>
            <LandingHeader fixed/>
            <main id={"page-main-content"}
                  className={styles.main}>
                {constrain ? (
                    <div id={"page-content"}
                         className={styles.content}>
                        {children}
                    </div>
                ) : children}
            </main>
            <SiteFooter/>
        </div>
    );
}
