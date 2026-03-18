import {Button, Card, Text, makeStyles, mergeClasses, tokens} from "@fluentui/react-components";
import {ChevronLeft20Regular, ChevronRight20Regular} from "@fluentui/react-icons";
import {useState} from "react";
import {
    BREAKPOINT_MOBILE,
    CARD_RADIUS,
    SPACE_MD,
    SPACE_SM,
    SPACE_XS,
    WIDTH_CONTENT,
} from "./shared.ts";

type IndustrySlide = {
    title: string;
    visualClass: string;
    screenshotSrc: string;
};

const useStyles = makeStyles({
    section: {
        maxWidth: WIDTH_CONTENT,
        margin: "0 auto",
        width: "100%",
        display: "grid",
        gap: SPACE_MD,
    },

    viewport: {
        overflow: "hidden",
        borderRadius: "3.125rem 3.125rem 0 0",
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackground2,

        [BREAKPOINT_MOBILE]: {
            borderRadius: "1.25rem 1.25rem 0 0",
        },
    },

    track: {
        display: "flex",
        transitionProperty: "transform",
        transitionDuration: "300ms",
        transitionTimingFunction: "ease",
        willChange: "transform",
    },

    slide: {
        minWidth: "100%",
    },

    imagePlaceholder: {
        aspectRatio: "1300 / 700",
        width: "100%",
        boxSizing: "border-box",
        position: "relative",
        overflow: "hidden",
        padding: "2.5rem 2.5rem 0",

        [BREAKPOINT_MOBILE]: {
            padding: "1.25rem 1.25rem 0",
        },
    },

    imageFrame: {
        position: "relative",
        width: "100%",
        height: "100%",
        overflow: "hidden",
        borderRadius: "0.875rem 0.875rem 0 0",
    },

    image: {
        position: "absolute",
        inset: 0,
        width: "100%",
        height: "100%",
        objectFit: "cover",
    },

    imageOverlay: {
        position: "absolute",
        left: SPACE_MD,
        bottom: SPACE_MD,
        zIndex: 1,
    },

    titleCard: {
        maxWidth: "32rem",
        padding: `${SPACE_SM} ${SPACE_MD}`,
        borderRadius: CARD_RADIUS,
        backgroundColor: tokens.colorNeutralBackground1,
        boxShadow: tokens.shadow8,
        color: tokens.colorNeutralStroke2,
    },

    titleText: {
        fontSize: tokens.fontSizeBase500,
        lineHeight: tokens.lineHeightBase500,

        [BREAKPOINT_MOBILE]: {
            fontSize: tokens.fontSizeBase300,
            lineHeight: tokens.lineHeightBase300,
        },
    },

    controls: {
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        gap: SPACE_MD,
    },

    indicators: {
        display: "flex",
        flexWrap: "wrap",
        justifyContent: "center",
        alignItems: "center",
        gap: SPACE_XS,
    },

    indicator: {
        width: "0.625rem",
        height: "0.625rem",
        borderRadius: "50%",
        border: "none",
        cursor: "pointer",
        backgroundColor: tokens.colorNeutralStroke2,
        transitionProperty: "background-color, transform",
        transitionDuration: "200ms",
        transitionTimingFunction: "ease",
    },

    indicatorActive: {
        backgroundColor: tokens.colorBrandForeground1,
        transform: "scale(1.2)",
    },

    industryVisualLaw: {
        background: "linear-gradient(135deg, #20344d 0%, #385980 55%, #7ea6d6 100%)",
    },

    industryVisualRealEstate: {
        background: "linear-gradient(135deg, #2d3953 0%, #4b678c 52%, #99b1cf 100%)",
    },

    industryVisualAccounting: {
        background: "linear-gradient(135deg, #2e3558 0%, #5c6da9 50%, #a2b5e8 100%)",
    },

    industryVisualHealthcare: {
        background: "linear-gradient(135deg, #24505a 0%, #3f8391 52%, #8fd0dc 100%)",
    },

    industryVisualBanking: {
        background: "linear-gradient(135deg, #243f63 0%, #3f6ba1 54%, #84b5ea 100%)",
    },
});

export function IndustryCarouselSection()
{
    const styles = useStyles();
    const [activeIndustrySlide, setActiveIndustrySlide] = useState<number>(0);

    const industrySlides: IndustrySlide[] = [
        {
            title: "Accounting & Audit Firms",
            visualClass: styles.industryVisualAccounting,
            screenshotSrc: "/demo-screenshots/app-screenshot-accounting.JPG",
        },
        {
            title: "Banks & Lending Institutions",
            visualClass: styles.industryVisualBanking,
            screenshotSrc: "/demo-screenshots/app-screenshot-banking-lending.JPG",
        },
        {
            title: "Law Firms & Legal Practices",
            visualClass: styles.industryVisualLaw,
            screenshotSrc: "/demo-screenshots/app-screenshot-legal.JPG",
        },
        {
            title: "Real Estate & Property Management",
            visualClass: styles.industryVisualRealEstate,
            screenshotSrc: "/demo-screenshots/app-screenshot-real-estate.JPG",
        },
        {
            title: "Healthcare & Medical Practices",
            visualClass: styles.industryVisualHealthcare,
            screenshotSrc: "/demo-screenshots/app-screenshot-healthcare.JPG",
        },
    ];

    const updateActiveSlide = (nextIndex: number) => {
        setActiveIndustrySlide(nextIndex);
        window.dispatchEvent(new CustomEvent("industry-slide-change", {
            detail: {index: nextIndex},
        }));
    };

    const goToPreviousIndustrySlide = () => {
        const nextIndex = activeIndustrySlide === 0 ? industrySlides.length - 1 : activeIndustrySlide - 1;
        updateActiveSlide(nextIndex);
    };

    const goToNextIndustrySlide = () => {
        updateActiveSlide((activeIndustrySlide + 1) % industrySlides.length);
    };

    return (
        <section className={styles.section} id="appImageSection">
            <div className={styles.viewport}>
                <div
                    className={styles.track}
                    style={{transform: `translateX(-${activeIndustrySlide * 100}%)`}}
                >
                    {industrySlides.map((slide) => (
                        <article key={slide.title} className={styles.slide}>
                            <div
                                className={mergeClasses(styles.imagePlaceholder, slide.visualClass)}
                                aria-label={`${slide.title} screenshot placeholder`}
                            >
                                <div className={styles.imageFrame}>
                                    <img
                                        className={styles.image}
                                        src={slide.screenshotSrc}
                                        alt={`${slide.title} app screenshot`}
                                        loading="lazy"
                                    />
                                    <Card className={mergeClasses(styles.titleCard, styles.imageOverlay, slide.visualClass)}>
                                        <Text weight="semibold" className={styles.titleText}>{slide.title}</Text>
                                    </Card>
                                </div>
                            </div>
                        </article>
                    ))}
                </div>
            </div>

            <div className={styles.controls}>
                <Button
                    appearance="outline"
                    icon={<ChevronLeft20Regular/>}
                    shape="circular"
                    onClick={goToPreviousIndustrySlide}
                    aria-label="Show previous industry screenshot"
                />
                <Text>
                    {activeIndustrySlide + 1}/{industrySlides.length}
                </Text>
                <Button
                    appearance="outline"
                    icon={<ChevronRight20Regular/>}
                    shape="circular"
                    onClick={goToNextIndustrySlide}
                    aria-label="Show next industry screenshot"
                />
            </div>

            <div className={styles.indicators}>
                {industrySlides.map((slide, index) => (
                    <button
                        key={slide.title}
                        type="button"
                        className={mergeClasses(
                            styles.indicator,
                            index === activeIndustrySlide && styles.indicatorActive,
                        )}
                        onClick={() => updateActiveSlide(index)}
                        aria-label={`Go to ${slide.title}`}
                        aria-current={index === activeIndustrySlide ? "true" : undefined}
                    />
                ))}
            </div>
        </section>
    );
}

