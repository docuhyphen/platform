import {Badge, Text, mergeClasses} from "@fluentui/react-components";
import {LinkButton} from "../../../shared/LinkButton.tsx";
import type {PricingTier} from "../pricingTeaserTiers.ts";
import {usePricingTeaserCardStyles} from "./PricingTeaserCardStyles.tsx";

interface PricingTeaserCardProps
{
    tier: PricingTier;
}

export function PricingTeaserCard({tier}: PricingTeaserCardProps)
{
    const styles = usePricingTeaserCardStyles();

    return (
        <article id={`pricing-teaser-card-${tier.name}`}
                 className={mergeClasses(styles.card, tier.featured && styles.cardFeatured)}>
            {tier.featured && (
                <span id={`pricing-teaser-featured-badge-${tier.name}`}
                      className={styles.featuredBadge}>Most popular</span>
            )}
            <Text id={`pricing-teaser-tier-name-${tier.name}`}
                  className={styles.tierName}>{tier.name}</Text>
            <div id={`pricing-teaser-meta-row-${tier.name}`}
                 className={styles.metaRow}>
                <span id={`pricing-teaser-meta-icon-${tier.name}`}
                      className={styles.metaIcon}
                      aria-hidden="true">B</span>
                <Text id={`pricing-teaser-best-for-${tier.name}`}
                      className={styles.bestFor}>{tier.bestFor}</Text>
            </div>
            <div id={`pricing-teaser-price-row-${tier.name}`}
                 className={styles.priceRow}>
                <Text id={`pricing-teaser-price-${tier.name}`}
                      className={styles.price}>{tier.price}</Text>
                <Text id={`pricing-teaser-cadence-${tier.name}`}
                      className={styles.cadence}>{tier.cadence}</Text>
            </div>
            <Text id={`pricing-teaser-blurb-${tier.name}`}
                  className={styles.blurb}>{tier.blurb}</Text>
            <ul id={`pricing-teaser-bullets-${tier.name}`}
                className={styles.bullets}>
                {tier.bullets.map((bullet, index) => (
                    <li id={`pricing-teaser-bullet-${tier.name}-${index + 1}`}
                        key={bullet}
                        className={styles.bullet}>
                        <Badge id={`pricing-teaser-bullet-badge-${tier.name}-${index + 1}`}
                               size="small"
                               appearance="filled"
                               className={styles.bulletBadge}/>
                        {bullet}
                    </li>
                ))}
            </ul>
            <LinkButton id={`pricing-teaser-cta-${tier.name}`}
                        to="/pricing"
                        appearance={tier.featured ? "primary" : "secondary"}
                        className={styles.cta}>
                {tier.cta}
            </LinkButton>
        </article>
    );
}
