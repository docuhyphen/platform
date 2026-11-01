import {Text, mergeClasses} from "@fluentui/react-components";
import {
    BranchFork24Regular,
    Document24Regular,
    History24Regular,
    PeopleTeam24Regular,
    ShieldLock24Regular,
} from "@fluentui/react-icons";
import {useAboutPageStyles} from "./AboutPageStyles.tsx";

export function AboutExchangeVisual()
{
    const styles = useAboutPageStyles();

    return (
        <div
            id="about-exchange-visual"
            className={styles.visual}
            aria-label="A controlled Exchange connecting people, documents, workflows, and audit history"
        >
            <div
                id="about-exchange-visual-core"
                className={styles.visualCore}
            >
                <ShieldLock24Regular id={"about-exchange-visual-shield-lock24-regular-1"}
                                     aria-hidden="true"/>
                <Text id="about-exchange-visual-core-label"
                      weight="semibold"
                      align={"center"}>One controlled Exchange</Text>
            </div>
            <div
                id="about-visual-people"
                className={mergeClasses(styles.visualPill, styles.pillPeople)}
            >
                <PeopleTeam24Regular id={"about-exchange-visual-people-team24-regular-1"}
                                     aria-hidden="true"/>
                People
            </div>
            <div
                id="about-visual-documents"
                className={mergeClasses(styles.visualPill, styles.pillDocuments)}
            >
                <Document24Regular id={"about-exchange-visual-document24-regular-1"}
                                   aria-hidden="true"/>
                Documents
            </div>
            <div
                id="about-visual-workflows"
                className={mergeClasses(styles.visualPill, styles.pillWorkflows)}
            >
                <BranchFork24Regular id={"about-exchange-visual-branch-fork24-regular-1"}
                                     aria-hidden="true"/>
                Workflows
            </div>
            <div
                id="about-visual-audit"
                className={mergeClasses(styles.visualPill, styles.pillAudit)}
            >
                <History24Regular id={"about-exchange-visual-history24-regular-1"}
                                  aria-hidden="true"/>
                Audit history
            </div>
        </div>
    );
}
