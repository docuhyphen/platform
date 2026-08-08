import {Button, Text, Title3} from "@fluentui/react-components";
import {SpeakToSalesDialog} from "../SpeakToSalesDialog.tsx";
import {SALES_EMAIL_URL} from "../shared.ts";
import {useGuidedDemoPanelStyles} from "./GuidedDemoPanelStyles.tsx";

interface GuidedDemoPanelProps
{
    idPrefix: string;
}

export function GuidedDemoPanel({idPrefix}: GuidedDemoPanelProps)
{
    const styles = useGuidedDemoPanelStyles();

    return (
        <section
            id={`${idPrefix}-guided-demo-panel`}
            className={styles.panel}
        >
            <Title3 as="h2">Want a guided demo?</Title3>
            <Text>
                Fill out a short form and our sales team will reach out within one
                business day to schedule a personalized walkthrough.
            </Text>
            <div
                id={`${idPrefix}-guided-demo-actions`}
                className={styles.actions}
            >
                <SpeakToSalesDialog
                    trigger={
                        <Button
                            id={`${idPrefix}-guided-demo-speak-to-sales`}
                            appearance="primary"
                            shape="circular"
                        >
                            Speak to sales
                        </Button>
                    }
                />
                <Button
                    id={`${idPrefix}-guided-demo-email`}
                    as="a"
                    href={SALES_EMAIL_URL}
                    appearance="secondary"
                    shape="circular"
                >
                    Email us directly
                </Button>
            </div>
        </section>
    );
}

