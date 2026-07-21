import {Button, Text} from "@fluentui/react-components";
import {SIGN_UP_URL} from "../shared.ts";
import {useFinalCtaSectionStyles} from "./FinalCtaSectionStyles.tsx";

export function FinalCtaSection()
{
    const styles = useFinalCtaSectionStyles();

    return (
        <section
            id="home-final-call-to-action"
            className={styles.wrapper}
            aria-labelledby="home-final-call-to-action-title"
        >
            <Text
                id="home-final-call-to-action-title"
                weight="regular"
                size={600}
            >
                Keep every document and decision in one auditable Exchange.
            </Text>
            <div
                id="home-final-call-to-action-actions"
                className={styles.buttonGroup}
            >
                <Button
                    id="home-final-start-free"
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
        </section>
    );
}

