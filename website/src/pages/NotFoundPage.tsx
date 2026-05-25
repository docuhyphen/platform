import {Text, Title1, makeStyles, tokens} from "@fluentui/react-components";
import {LinkButton} from "../shared/LinkButton.tsx";
import {PageShell} from "../shared/PageShell.tsx";
import {SPACE_MD, SPACE_SM} from "../landing/shared.ts";

const useStyles = makeStyles({
    wrapper: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        textAlign: "center",
        gap: SPACE_SM,
        padding: "4rem 1rem",
    },

    code: {
        color: tokens.colorBrandForeground1,
        fontSize: "5rem",
        fontWeight: tokens.fontWeightSemibold,
        lineHeight: 1,
    },

    title: {
        color: tokens.colorNeutralForeground1,
    },

    blurb: {
        color: tokens.colorNeutralForeground2,
        maxWidth: "32rem",
    },

    actions: {
        display: "flex",
        gap: SPACE_MD,
        flexWrap: "wrap",
        justifyContent: "center",
        marginTop: SPACE_MD,
    },
});

export function NotFoundPage()
{
    const styles = useStyles();

    return (
        <PageShell>
            <section className={styles.wrapper}>
                <Text className={styles.code}>404</Text>
                <Title1 className={styles.title}>We can&apos;t find that page</Title1>
                <Text size={500} className={styles.blurb}>
                    The page may have moved or never existed. Try heading home or browsing our resources.
                </Text>
                <div className={styles.actions}>
                    <LinkButton to="/" appearance="primary" shape="circular">
                        Back home
                    </LinkButton>
                    <LinkButton to="/resources" appearance="outline" shape="circular">
                        Browse resources
                    </LinkButton>
                </div>
            </section>
        </PageShell>
    );
}
