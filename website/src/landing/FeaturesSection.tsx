import {
    Badge,
    Button,
    Card,
    CardHeader,
    Text,
    Title2,
    makeStyles,
    mergeClasses,
    tokens
} from "@fluentui/react-components";
import {ChevronLeft20Regular, ChevronRight20Regular} from "@fluentui/react-icons";
import {useState} from "react";
import {Link} from "react-router-dom";
import {
    BREAKPOINT_MOBILE,
    CARD_RADIUS,
    SECTION_PADDING_DESKTOP,
    SECTION_PADDING_MOBILE,
    SPACE_LG,
    SPACE_MD,
    SPACE_SM,
    SPACE_XS,
    WIDTH_CONTENT, WIDTH_SUBTITLE,
} from "./shared.ts";

type FeatureItem = {
    title: string;
    body: string;
    imageSrc: string;
    imageAlt: string;
};

type IndustrySlide = {
    title: string;
    visualClass: string;
    screenshotSrc: string;
    slug: string;
};

const useStyles = makeStyles({
    introWrapper: {
        padding: SECTION_PADDING_DESKTOP,
        boxSizing: "border-box",
        marginTop: "4rem",

        [BREAKPOINT_MOBILE]: {
            padding: SECTION_PADDING_MOBILE,
        },
    },

    intro: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_LG,
        maxWidth: WIDTH_CONTENT,
        margin: "0 auto",
        alignItems: "flex-start",
    },

    sectionTitle: {
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
        fontSize: tokens.fontSizeHero800
    },

    carouselSection: {
        maxWidth: WIDTH_CONTENT,
        margin: "0 auto",
        width: "100%",
        display: "grid",
        gap: SPACE_MD,
    },

    carouselViewport: {
        overflow: "hidden",
        borderRadius: "3.125rem 3.125rem 0 0",
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackground2,

        [BREAKPOINT_MOBILE]: {
            borderRadius: "1.25rem 1.25rem 0 0",
        },
    },

    carouselTrack: {
        display: "flex",
        transitionProperty: "transform",
        transitionDuration: "300ms",
        transitionTimingFunction: "ease",
        willChange: "transform",
    },

    carouselSlide: {
        minWidth: "100%",
    },

    carouselImagePlaceholder: {
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

    carouselImageFrame: {
        position: "relative",
        width: "100%",
        height: "100%",
        overflow: "hidden",
        borderRadius: "0.875rem 0.875rem 0 0",
    },

    carouselImage: {
        position: "absolute",
        inset: 0,
        width: "100%",
        height: "100%",
        objectFit: "cover",
    },

    carouselImageOverlay: {
        position: "absolute",
        left: SPACE_MD,
        bottom: SPACE_MD,
        zIndex: 1,
    },

    carouselTitleCard: {
        maxWidth: "32rem",
        padding: `${SPACE_SM} ${SPACE_MD}`,
        borderRadius: CARD_RADIUS,
        backgroundColor: tokens.colorNeutralBackground1,
        boxShadow: tokens.shadow8,
        color: tokens.colorNeutralStroke2,
    },

    carouselTitleText: {
        fontSize: tokens.fontSizeBase500,
        lineHeight: tokens.lineHeightBase500,

        [BREAKPOINT_MOBILE]: {
            fontSize: tokens.fontSizeBase300,
            lineHeight: tokens.lineHeightBase300,
        },
    },

    carouselControls: {
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        gap: SPACE_MD,
    },

    carouselIndicators: {
        display: "flex",
        flexWrap: "wrap",
        justifyContent: "center",
        alignItems: "center",
        gap: SPACE_XS,
    },

    carouselIndicator: {
        width: "0.625rem",
        height: "0.625rem",
        borderRadius: "50%",
        border: "none",
        cursor: "pointer",
        backgroundColor: tokens.colorNeutralStroke1,
        transitionProperty: "background-color, transform",
        transitionDuration: "200ms",
        transitionTimingFunction: "ease",
    },

    carouselIndicatorActive: {
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

    cardsWrapper: {
        padding: SECTION_PADDING_DESKTOP,
        boxSizing: "border-box",

        [BREAKPOINT_MOBILE]: {
            padding: SECTION_PADDING_MOBILE,
        },
    },

    cards: {
        margin: "0 auto",
        maxWidth: WIDTH_CONTENT,
        display: "grid",
        gridTemplateColumns: "repeat(3, 1fr)",
        gap: SPACE_LG,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr",
            gap: SPACE_MD,
        },
    },

    featureCard: {
        // Large internal spacing so image/content don't touch card edges
        padding: SPACE_LG,
        display: "flex",
        flexDirection: "column",
        backgroundColor: "#2e3558",
        borderBottomWidth: "0",
        borderBottomStyle: "none",
        borderBottomColor: "transparent",

        [BREAKPOINT_MOBILE]: {
            padding: SPACE_MD,
        },
    },

    featureThemeAccounting: {
        backgroundColor: "#2e3558",
        backgroundImage: "linear-gradient(135deg, #2e3558 0%, #5c6da9 50%, #a2b5e8 100%)",
        boxShadow: "0 10px 24px rgba(46, 53, 88, 0.28)",
    },

    featureThemeBanking: {
        backgroundColor: "#243f63",
        backgroundImage: "linear-gradient(135deg, #243f63 0%, #3f6ba1 54%, #84b5ea 100%)",
        boxShadow: "0 10px 24px rgba(36, 63, 99, 0.28)",
    },

    featureThemeLegal: {
        backgroundColor: "#20344d",
        backgroundImage: "linear-gradient(135deg, #20344d 0%, #385980 55%, #7ea6d6 100%)",
        boxShadow: "0 10px 24px rgba(32, 52, 77, 0.28)",
    },

    featureThemeRealEstate: {
        backgroundColor: "#2d3953",
        backgroundImage: "linear-gradient(135deg, #2d3953 0%, #4b678c 52%, #99b1cf 100%)",
        boxShadow: "0 10px 24px rgba(45, 57, 83, 0.28)",
    },

    featureThemeHealthcare: {
        backgroundColor: "#24505a",
        backgroundImage: "linear-gradient(135deg, #24505a 0%, #3f8391 52%, #8fd0dc 100%)",
        boxShadow: "0 10px 24px rgba(36, 80, 90, 0.28)",
    },

    featureImage: {
        width: "100%",
        height: "11rem",
        objectFit: "cover",
        display: "block",
        borderTopLeftRadius: "0.75rem",
        borderTopRightRadius: "0.75rem",
        borderBottomLeftRadius: 0,
        borderBottomRightRadius: 0,
        marginBottom: SPACE_MD,
    },

    cardBody: {
        background: "rgba(0,0,0,0.20)",
        padding: "16px 8px",
        flex: 1,
        borderRadius: "0.6rem",
    },

    featureTitleText: {
        color: "#f3f7ff",
    },

    featureBadge: {
        backgroundColor: "rgba(255, 255, 255, 0.2)",
    },

    featureBody: {
        color: "#d6e1f2",
        paddingTop: "1.2rem"
    },

    subheading: {
        maxWidth: WIDTH_SUBTITLE,
        color: tokens.colorNeutralForeground1,
        fontWeight: "100"
    },
});

export function FeaturesSection()
{
    const styles = useStyles();
    const [activeIndustrySlide, setActiveIndustrySlide] = useState<number>(0);

    const featureThemeClasses = [
        styles.featureThemeRealEstate,
        styles.featureThemeLegal,
        styles.featureThemeHealthcare,
        styles.featureThemeAccounting,
        styles.featureThemeBanking,
    ];
    const activeThemeClass = featureThemeClasses[activeIndustrySlide] || styles.featureThemeAccounting;

    const industrySlides: IndustrySlide[] = [
        {
            title: "Real Estate & Property Management",
            visualClass: styles.industryVisualRealEstate,
            screenshotSrc: "/demo-screenshots/app-screenshot-real-estate.JPG",
            slug: "real-estate",
        },
        {
            title: "Law Firms & Legal Practices",
            visualClass: styles.industryVisualLaw,
            screenshotSrc: "/demo-screenshots/app-screenshot-legal.JPG",
            slug: "legal",
        },
        {
            title: "Healthcare & Medical Practices",
            visualClass: styles.industryVisualHealthcare,
            screenshotSrc: "/demo-screenshots/app-screenshot-healthcare.JPG",
            slug: "healthcare",
        },
        {
            title: "Accounting & Audit Firms",
            visualClass: styles.industryVisualAccounting,
            screenshotSrc: "/demo-screenshots/app-screenshot-accounting.JPG",
            slug: "accounting",
        },
        {
            title: "Banks & Lending Institutions",
            visualClass: styles.industryVisualBanking,
            screenshotSrc: "/demo-screenshots/app-screenshot-banking-lending.JPG",
            slug: "banking",
        },
    ];

    const goToPreviousIndustrySlide = () =>
    {
        setActiveIndustrySlide((current) => current === 0 ? industrySlides.length - 1 : current - 1);
    };

    const goToNextIndustrySlide = () =>
    {
        setActiveIndustrySlide((current) => (current + 1) % industrySlides.length);
    };

    const features: FeatureItem[] = [
        {
            title: "Structured Document Requests",
            body: "Create controlled share sessions to request documents from customers or partners, with defined access, expiry controls, and full visibility.",
            imageSrc: "/demo-screenshots/structured-document-requests.JPG",
            imageAlt: "Structured document requests screenshot",
        },
        {
            title: "Secure Document Delivery",
            body: "Deliver financial, legal, and confidential documents without email attachments or public links, ensuring only authorized recipients can access them.",
            imageSrc: "/demo-screenshots/secure-document-delivery.png",
            imageAlt: "Secure document delivery screenshot",
        },
        {
            title: "Full Audit & Compliance Logging",
            body: "Every upload, view, download, and action is recorded, giving you defensible audit trails and supporting regulatory compliance.",
            imageSrc: "/demo-screenshots/full-audit.JPG",
            imageAlt: "Full audit and compliance logging screenshot",
        },
    ];

    return (
        <>
            <section className={styles.introWrapper}>
                <section className={styles.intro}>
                    <Title2 className={styles.sectionTitle}>
                        Secure, Controlled &amp; Compliant Document Exchange
                    </Title2>
                    <Text size={500} className={styles.subheading}>
                        A purpose-built platform designed specifically for exchanging
                        sensitive business and customer documents, securely, transparently,
                        and compliantly.
                    </Text>
                </section>
            </section>
            <section className={styles.carouselSection}>
                <div className={styles.carouselViewport}>
                    <div
                        className={styles.carouselTrack}
                        style={{transform: `translateX(-${activeIndustrySlide * 100}%)`}}
                    >
                        {industrySlides.map((slide) => (
                            <article key={slide.title} className={styles.carouselSlide}>
                                <Link
                                    to={`/solutions/${slide.slug}`}
                                    className={mergeClasses(styles.carouselImagePlaceholder, slide.visualClass)}
                                    aria-label={`View ${slide.title} solution`}
                                    style={{display: "block", textDecoration: "none"}}
                                >
                                    <div className={styles.carouselImageFrame}>
                                        <img
                                            className={styles.carouselImage}
                                            src={slide.screenshotSrc}
                                            alt={`${slide.title} app screenshot`}
                                            loading="lazy"
                                        />
                                        <Card
                                            className={mergeClasses(styles.carouselTitleCard, styles.carouselImageOverlay, slide.visualClass)}>
                                            <Text weight="semibold"
                                                  className={styles.carouselTitleText}>{slide.title}</Text>
                                        </Card>
                                    </div>
                                </Link>
                            </article>
                        ))}
                    </div>
                </div>

                <div className={styles.carouselControls}>
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

                <div className={styles.carouselIndicators}>
                    {industrySlides.map((slide, index) => (
                        <button
                            key={slide.title}
                            type="button"

                            className={mergeClasses(
                                styles.carouselIndicator,
                                index === activeIndustrySlide && styles.carouselIndicatorActive,
                            )}
                            onClick={() => setActiveIndustrySlide(index)}
                            aria-label={`Go to ${slide.title}`}
                            aria-current={index === activeIndustrySlide ? "true" : undefined}
                        />
                    ))}
                </div>
            </section>

            <section className={styles.cardsWrapper}>
                <div className={styles.cards}>
                    {features.map((feature) => (
                        <Card key={feature.title} appearance="filled"
                              className={mergeClasses(styles.featureCard, activeThemeClass)}>
                            <img
                                className={styles.featureImage}
                                src={feature.imageSrc}
                                alt={feature.imageAlt}
                                loading="lazy"
                            />
                            <div className={styles.cardBody}>
                                <CardHeader
                                    header={
                                        <Badge size="extra-large" appearance="filled" className={styles.featureBadge}>
                                            <Text weight="bold"
                                                  className={styles.featureTitleText}>{feature.title}</Text>
                                        </Badge>
                                    }
                                />
                                <div className={styles.featureBody}>{feature.body}</div>
                            </div>
                        </Card>
                    ))}
                </div>
            </section>
        </>);
}
