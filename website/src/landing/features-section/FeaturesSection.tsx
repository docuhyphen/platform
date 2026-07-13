import {IndustryExperience} from "./IndustryExperience.tsx";
import {useFeaturesSectionStyles} from "./FeaturesSectionStyles.tsx";

interface FeaturesSectionProps
{
    initialIndustrySlug?: string;
}

export function FeaturesSection({initialIndustrySlug}: FeaturesSectionProps)
{
    const styles = useFeaturesSectionStyles();

    return (
        <section
            id="features-section"
            className={styles.section}
            aria-labelledby="industry-product-experience-heading"
        >
            <IndustryExperience initialIndustrySlug={initialIndustrySlug}/>
        </section>
    );
}
