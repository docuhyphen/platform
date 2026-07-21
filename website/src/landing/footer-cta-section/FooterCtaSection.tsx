import {Button, Text} from "@fluentui/react-components";
import {SIGN_UP_URL} from "../shared.ts";
import {useFooterCtaSectionStyles} from "./FooterCtaSectionStyles.tsx";

export function FooterCtaSection()
{
    const styles = useFooterCtaSectionStyles();

    return (
        <footer
            id="home-footer-call-to-action"
            className={styles.wrapper}
        >
            <Text
                id="home-footer-call-to-action-title"
                weight="regular"
                size={600}
            >
                Keep every document and decision in one auditable Exchange.
            </Text>
            <div
                id="home-footer-call-to-action-actions"
                className={styles.buttonGroup}
            >
                <Button
                    id="home-footer-start-free"
                    appearance="primary"
                    as="a"
                    size="large"
                    target="_blank"
                    rel="noopener noreferrer"
                    shape="circular"
                    className={styles.button}
                    href={SIGN_UP_URL}
                >
                    Start Free
                </Button>
            </div>
        </footer>
    );
}
