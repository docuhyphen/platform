import {mergeClasses} from "@fluentui/react-components";
import type {IndustryExperience} from "./featureContent.ts";
import {useIndustryExperienceStyles} from "./IndustryExperienceStyles.tsx";

interface IndustryScreenshotProps
{
    industry: IndustryExperience;
}

export function IndustryScreenshot({industry}: IndustryScreenshotProps)
{
    const styles = useIndustryExperienceStyles();

    return (
        <div
            id="industry-product-experience-browser"
            className={styles.browser}
        >
            <div
                id="industry-product-experience-browser-frame"
                className={styles.browserFrame}
            >
                <div
                    id="industry-product-experience-browser-bar"
                    className={styles.browserBar}
                    aria-hidden="true"
                >
                    <span
                        id="industry-browser-dot-neutral"
                        className={styles.browserDot}
                    />
                    <span
                        id="industry-browser-dot-brand"
                        className={mergeClasses(styles.browserDot, styles.browserDotBrand)}
                    />
                    <span
                        id="industry-browser-dot-success"
                        className={mergeClasses(styles.browserDot, styles.browserDotSuccess)}
                    />
                </div>
                <img
                    id="industry-product-experience-image"
                    className={styles.image}
                    src={industry.screenshotSrc}
                    alt={`${industry.title} app screenshot`}
                    loading="lazy"
                />
            </div>
        </div>
    );
}
