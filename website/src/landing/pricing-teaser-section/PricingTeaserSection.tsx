import {Text, Title2} from "@fluentui/react-components";
import {PricingTeaserCard} from "./pricing-teaser-card/PricingTeaserCard.tsx";
import {pricingTiers} from "./pricingTeaserTiers.ts";
import {usePricingTeaserSectionStyles} from "./PricingTeaserSectionStyles.tsx";

export function PricingTeaserSection()
{
    const styles = usePricingTeaserSectionStyles();

    return (
        <section id={"pricing-teaser-section"}
                 className={styles.wrapper}>
            <div id={"pricing-teaser-container"}
                 className={styles.container}>
                <div id={"pricing-teaser-intro"}
                     className={styles.intro}>
                    <Title2 id={"pricing-teaser-title"}
                            className={styles.sectionTitle}>Pricing built for every stage</Title2>
                    <Text id={"pricing-teaser-subheading"}
                          size={500}
                          className={styles.subheading}
                          align={"center"}>
                        Start free. Scale with controls when you need them. Monthly billing, cancel anytime.
                    </Text>
                </div>

                <div id={"pricing-teaser-grid"}
                     className={styles.grid}>
                    {pricingTiers.map((tier) => (
                        <PricingTeaserCard key={tier.name}
                                           tier={tier}/>
                    ))}
                </div>
            </div>
        </section>
    );
}
