import {Text, Title1} from "@fluentui/react-components";
import {LinkButton} from "../../shared/LinkButton.tsx";
import {PageShell} from "../../shared/page-shell/PageShell.tsx";
import {useNotFoundPageStyles} from "./NotFoundPageStyles.tsx";

export function NotFoundPage()
{
    const styles = useNotFoundPageStyles();

    return (
        <PageShell>
            <section id={"not-found-page-section-1"}
                     className={styles.wrapper}>
                <Text id={"not-found-page-text-1"}
                      className={styles.code}>404</Text>
                <Title1 id={"not-found-page-title"}
                        as="h1"
                        className={styles.title}>
                    We can&apos;t find that page
                </Title1>
                <Text id={"not-found-page-text-2"}
                      size={500}
                      className={styles.blurb}>
                    The page may have moved or never existed. Return to the homepage or contact our team for help.
                </Text>
                <div id={"not-found-page-div-1"}
                     className={styles.actions}>
                    <LinkButton id={"not-found-page-link-button-1"}
                                to="/"
                                appearance="primary">
                        Back home
                    </LinkButton>
                    <LinkButton id={"not-found-page-link-button-2"}
                                to="/#home-contact-info"
                                appearance="secondary">
                        Contact DocuHyphen
                    </LinkButton>
                </div>
            </section>
        </PageShell>
    );
}
