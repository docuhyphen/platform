import {Button, Text, makeStyles} from "@fluentui/react-components";
import {
    BUTTON_MIN_WIDTH,
    SIGN_UP_URL,
    SPACE_SM,
} from "./shared.ts";

const useStyles = makeStyles({
    wrapper: {
        textAlign: "center",
        padding: "3rem 1rem",
        background: "linear-gradient(-360deg, #f7efe9 0%, #ffffff 100%)",
    },

    button: {
        minWidth: BUTTON_MIN_WIDTH,
    },

    buttonGroup: {
        marginTop: SPACE_SM,
    },
});

export function FooterCtaSection()
{
    const styles = useStyles();

    return (
        <footer className={styles.wrapper}>
            <Text weight="regular" size={600}>
                Take Control of Your Sensitive Documents
            </Text>
            <div className={styles.buttonGroup}>
                <Button
                    appearance="primary"
                    as="a"
                    size="large"
                    target="_blank"
                    rel="noopener noreferrer"
                    shape="circular"
                    className={styles.button}
                    href={SIGN_UP_URL}
                >
                    Try it for free
                </Button>
            </div>
        </footer>
    );
}

