import {Text} from "@fluentui/react-components";
import {ChevronRight16Regular} from "@fluentui/react-icons";
import {Link} from "react-router-dom";
import {Fragment} from "react";
import {useBreadcrumbsStyles} from "./BreadcrumbsStyles.tsx";

type Crumb = {
    label: string;
    to?: string;
};

export function Breadcrumbs({trail}: {trail: Crumb[]})
{
    const styles = useBreadcrumbsStyles();

    return (
        <nav id={"page-breadcrumbs"}
             className={styles.wrapper}
             aria-label="Breadcrumb">
            {trail.map((crumb, i) => {
                const isLast = i === trail.length - 1;
                return (
                    <Fragment key={`${crumb.label}-${i}`}>
                        {crumb.to && !isLast ? (
                            <Link id={`breadcrumb-${i}`}
                                  to={crumb.to}
                                  className={styles.link}>
                                {crumb.label}
                            </Link>
                        ) : (
                            <Text id={`breadcrumb-${i}`}
                                  className={isLast ? styles.current : undefined}
                                  aria-current={isLast ? "page" : undefined}>
                                {crumb.label}
                            </Text>
                        )}
                        {!isLast && <ChevronRight16Regular id={`breadcrumb-separator-${i}`}
                                                           className={styles.separator}/>}
                    </Fragment>
                );
            })}
        </nav>
    );
}
