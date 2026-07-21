import {Text, makeStyles, tokens} from "@fluentui/react-components";
import {ChevronRight16Regular} from "@fluentui/react-icons";
import {Link} from "react-router-dom";
import {Fragment} from "react";

type Crumb = {
    label: string;
    to?: string;
};

const useStyles = makeStyles({
    wrapper: {
        display: "flex",
        flexWrap: "wrap",
        alignItems: "center",
        gap: "0.25rem",
        fontSize: tokens.fontSizeBase200,
        color: tokens.colorNeutralForeground3,
    },

    link: {
        color: tokens.colorNeutralForeground3,
        textDecorationLine: "none",

        ":hover": {
            color: tokens.colorBrandForeground1,
            textDecorationLine: "underline",
        },
    },

    current: {
        color: tokens.colorNeutralForeground1,
        fontWeight: tokens.fontWeightSemibold,
    },

    separator: {
        color: tokens.colorNeutralForeground4,
        display: "inline-flex",
    },
});

export function Breadcrumbs({trail}: {trail: Crumb[]})
{
    const styles = useStyles();

    return (
        <nav
            id="page-breadcrumbs"
            className={styles.wrapper}
            aria-label="Breadcrumb"
        >
            {trail.map((crumb, i) => {
                const isLast = i === trail.length - 1;
                return (
                    <Fragment key={`${crumb.label}-${i}`}>
                        {crumb.to && !isLast ? (
                            <Link
                                id={`breadcrumb-${i}`}
                                to={crumb.to}
                                className={styles.link}
                            >
                                {crumb.label}
                            </Link>
                        ) : (
                            <Text
                                id={`breadcrumb-${i}`}
                                className={isLast ? styles.current : undefined}
                                aria-current={isLast ? "page" : undefined}
                            >
                                {crumb.label}
                            </Text>
                        )}
                        {!isLast && <ChevronRight16Regular className={styles.separator}/>}
                    </Fragment>
                );
            })}
        </nav>
    );
}
