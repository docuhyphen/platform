import {Button, LargeTitle, Text, mergeClasses} from "@fluentui/react-components";
import {SIGN_UP_URL} from "../shared.ts";
import {ExchangeOrchestrationCanvas} from "./exchange-orchestration-canvas/ExchangeOrchestrationCanvas.tsx";
import {useHeroSectionStyles} from "./HeroSectionStyles.tsx";

export function HeroSection()
{
    const styles = useHeroSectionStyles();

    return (
        <section
            id="home-hero"
            className={styles.wrapper}
        >
            <div
                id="home-hero-content"
                className={styles.container}
            >
                <div
                    id="home-hero-copy"
                    className={styles.content}
                >
                    <LargeTitle
                        id="home-hero-title"
                        as="h1"
                        className={styles.title}
                    >
                        One Secure Workspace for Every Document-Driven Business Process
                    </LargeTitle>
                    <Text
                        id="home-hero-supporting-text"
                        className={styles.supportingText}
                    >
                        Securely exchange documents, automate workflows, and collaborate seamlessly with clients, teams,
                        and partners using configurable workflows built for your business.
                    </Text>
                    <div
                        id="home-hero-actions"
                        className={styles.actions}
                    >
                        <Button
                            id="home-hero-start-free"
                            appearance="primary"
                            as="a"
                            className={mergeClasses(styles.buttonBase, styles.primaryCta)}
                            shape="circular"
                            target="_blank"
                            rel="noopener noreferrer"
                            href={SIGN_UP_URL}
                        >
                            Start Free
                        </Button>
                    </div>
                </div>
                <ExchangeOrchestrationCanvas/>
            </div>
        </section>
    );
}
