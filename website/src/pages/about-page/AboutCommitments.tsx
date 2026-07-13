import {Text, Title2} from "@fluentui/react-components";
import {CheckmarkCircle24Regular} from "@fluentui/react-icons";
import {useAboutPageStyles} from "./AboutPageStyles.tsx";

export function AboutCommitments()
{
    const styles = useAboutPageStyles();

    return (
        <section
            id="about-commitments"
            className={styles.statement}
        >
            <Title2
                id="about-commitments-title"
                className={styles.statementTitle}
            >
                Security is the baseline, not an upgrade.
            </Title2>
            <div
                id="about-commitment-list"
                className={styles.commitments}
            >
                <Text
                    id="about-commitment-audit"
                    className={styles.commitment}
                >
                    <CheckmarkCircle24Regular aria-hidden="true"/>Readable audit trails
                </Text>
                <Text
                    id="about-commitment-data"
                    className={styles.commitment}
                >
                    <CheckmarkCircle24Regular aria-hidden="true"/>Customer-owned data
                </Text>
                <Text
                    id="about-commitment-controls"
                    className={styles.commitment}
                >
                    <CheckmarkCircle24Regular aria-hidden="true"/>Controls built into every Exchange
                </Text>
            </div>
        </section>
    );
}
