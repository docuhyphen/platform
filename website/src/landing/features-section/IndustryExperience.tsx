import {Button, Tab, TabList, Text, Title2} from "@fluentui/react-components";
import {Open20Regular} from "@fluentui/react-icons";
import {useEffect, useRef, useState} from "react";
import {industryExperiences} from "./featureContent.ts";
import {IndustryProductDemo} from "./industry-product-demo/IndustryProductDemo.tsx";
import {useIndustryExperienceStyles} from "./IndustryExperienceStyles.tsx";

interface IndustryExperienceProps
{
    initialIndustrySlug?: string;
}

function getInitialIndustrySlug(initialIndustrySlug?: string): string
{
    return industryExperiences.some((industry) => industry.slug === initialIndustrySlug)
        ? initialIndustrySlug ?? industryExperiences[0].slug
        : industryExperiences[0].slug;
}

const MOBILE_MEDIA_QUERY = "(max-width: 48em)";

export function IndustryExperience({initialIndustrySlug}: IndustryExperienceProps)
{
    const styles = useIndustryExperienceStyles();
    const tabViewportRef = useRef<HTMLDivElement>(null);
    const tabRefs = useRef<Record<string, HTMLElement | null>>({});
    const [activeIndustrySlug, setActiveIndustrySlug] = useState(() => getInitialIndustrySlug(initialIndustrySlug));
    const activeIndustry = industryExperiences.find((industry) => industry.slug === activeIndustrySlug)
        ?? industryExperiences[0];

    useEffect(() =>
    {
        setActiveIndustrySlug(getInitialIndustrySlug(initialIndustrySlug));
    }, [initialIndustrySlug]);

    useEffect(() =>
    {
        if (!window.matchMedia(MOBILE_MEDIA_QUERY).matches) return;

        const tabViewport = tabViewportRef.current;
        const activeTab = tabRefs.current[activeIndustrySlug];
        if (!tabViewport || !activeTab) return;

        const targetScrollLeft = activeTab.offsetLeft - ((tabViewport.clientWidth - activeTab.clientWidth) / 2);
        tabViewport.scrollTo({
            left: Math.max(0, targetScrollLeft),
            behavior: "smooth",
        });
    }, [activeIndustrySlug]);

    return (
        <section
            id="industry-product-experience"
            className={styles.section}
            aria-labelledby="industry-product-experience-heading"
        >
            <div
                id="industry-product-experience-introduction"
                className={styles.introduction}
            >
                <Title2
                    id="industry-product-experience-heading"
                    className={styles.heading}
                >
                    Built to solve document driven processes,<br/> and  adapt to your industry.
                </Title2>
                <Text
                    id="industry-product-experience-description"
                    className={styles.description}
                    size={500}
                >
                    Explore how the platform supports document-driven business processes, controls, and collaboration needs of your industry.
                </Text>
            </div>

            <div
                id="industry-product-experience-tab-viewport"
                className={styles.tabViewport}
                ref={tabViewportRef}
            >
                <TabList
                    id="industry-product-experience-tabs"
                    className={styles.tabs}
                    selectedValue={activeIndustrySlug}
                    onTabSelect={(_, data) =>
                    {
                        if (typeof data.value === "string") setActiveIndustrySlug(data.value);
                    }}
                >
                    {industryExperiences.map((industry) => (
                        <Tab
                            id={`industry-product-experience-tab-${industry.slug}`}
                            key={industry.slug}
                            value={industry.slug}
                            ref={(element) =>
                            {
                                tabRefs.current[industry.slug] = element;
                            }}
                        >
                            {industry.title}
                        </Tab>
                    ))}
                </TabList>
            </div>

            <article
                id="industry-product-experience-selected"
                className={styles.experience}
                aria-live="polite"
            >
                <div
                    id="industry-product-experience-summary"
                    className={styles.summary}
                >
                    <div
                        id="industry-product-experience-copy"
                        className={styles.copy}
                    >
                        <Text
                            id="industry-product-experience-name"
                            className={styles.industryName}
                        >
                            {activeIndustry.title}
                        </Text>
                        <Text
                            id="industry-product-experience-outcome"
                            className={styles.outcome}
                        >
                            {activeIndustry.outcome}
                        </Text>
                    </div>
                    <Button
                        id="industry-product-experience-action"
                        appearance="primary"
                        as="a"
                        size={"small"}
                        className={styles.action}
                        href={`/industries/${activeIndustry.slug}`}
                        icon={<Open20Regular aria-hidden="true"/>}
                        iconPosition="after"
                        shape="circular"
                    >
                        Explore this solution
                    </Button>
                </div>

                <IndustryProductDemo industry={activeIndustry}/>
            </article>
        </section>
    );
}
