import {Button, Text, Title1, mergeClasses} from "@fluentui/react-components";
import type {IndustryExperience} from "../landing/features-section/featureContent.ts";
import {IndustryProductDemo} from "../landing/features-section/industry-product-demo/IndustryProductDemo.tsx";
import {SIGN_UP_URL} from "../landing/shared.ts";
import {SpeakToSalesDialog} from "../landing/SpeakToSalesDialog.tsx";
import type {IndustriesContent} from "./IndustriesPageContent.ts";
import {useIndustriesPageStyles} from "./IndustriesPageStyles.tsx";

interface IndustriesPageHeroProps
{
    data: IndustriesContent;
    demoIndustry: IndustryExperience;
}

export function IndustriesPageHero({data, demoIndustry}: IndustriesPageHeroProps)
{
    const styles = useIndustriesPageStyles();

    return (
        <section
            id="industry-detail-hero"
            className={mergeClasses(styles.hero, styles[data.heroClassName])}
        >
            <div
                id="industry-detail-hero-text"
                className={styles.heroText}
            >
                <Text
                    id="industry-detail-eyebrow"
                    className={styles.heroEyebrow}
                >
                    Industries / {data.name}
                </Text>
                <Title1
                    id="industry-detail-title"
                    className={styles.heroTitle}
                >
                    {data.headline}
                </Title1>
                <Text
                    id="industry-detail-blurb"
                    size={500}
                    className={styles.heroBlurb}
                >
                    {data.blurb}
                </Text>
                <div
                    id="industry-detail-hero-actions"
                    className={styles.heroActions}
                >
                    <Button
                        id="industry-detail-start-free"
                        appearance="primary"
                        as="a"
                        href={SIGN_UP_URL}
                        target="_blank"
                        rel="noopener noreferrer"
                        shape="circular"
                    >
                        Start Free
                    </Button>
                    <SpeakToSalesDialog
                        trigger={(
                            <Button
                                id="industry-detail-speak-to-sales"
                                appearance="secondary"
                                shape="circular"
                            >
                                Speak to sales
                            </Button>
                        )}
                    />
                </div>
            </div>
            <div
                id="industry-detail-product-demo"
                className={styles.heroDemo}
            >
                <IndustryProductDemo industry={demoIndustry}/>
            </div>
        </section>
    );
}
