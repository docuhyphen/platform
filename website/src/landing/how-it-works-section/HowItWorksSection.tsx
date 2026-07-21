import {mergeClasses, Text, Title2, Title3} from "@fluentui/react-components";
import {useEffect, useRef, useState} from "react";
import {useHowItWorksSectionStyles} from "./HowItWorksSectionStyles.tsx";

type HowItWorksStep = {
    number: string;
    title: string;
    description: string;
    illustrationLabel: string;
    illustrationClass: string;
    animationClass: string;
};

const steps: HowItWorksStep[] = [
    {
        number: "01",
        title: "Create an Exchange",
        description: "Bring together the documents, people, and context for a single piece of work.",
        illustrationLabel: "A secure document Exchange being created",
        illustrationClass: "illustrationOne",
        animationClass: "stepFirst",
    },
    {
        number: "02",
        title: "Set access and workflow",
        description: "Assign the right roles and configure the workflow that guides each Exchange.",
        illustrationLabel: "People, access controls, and workflow steps",
        illustrationClass: "illustrationTwo",
        animationClass: "stepSecond",
    },
    {
        number: "03",
        title: "Work through the process",
        description: "Participants collaborate, review, respond, and complete the steps relevant to them.",
        illustrationLabel: "Participants collaborating on a document",
        illustrationClass: "illustrationThree",
        animationClass: "stepThird",
    },
    {
        number: "04",
        title: "Finish with a clear record",
        description: "Close the Exchange with an auditable history of activity, decisions, and outcomes.",
        illustrationLabel: "A completed Exchange with an activity record",
        illustrationClass: "illustrationFour",
        animationClass: "stepFourth",
    },
];

export function HowItWorksSection()
{
    const styles = useHowItWorksSectionStyles();
    const sectionRef = useRef<HTMLElement>(null);
    const [isVisible, setIsVisible] = useState(false);

    useEffect(() =>
    {
        const section = sectionRef.current;
        if (!section) return;

        const observer = new IntersectionObserver(
            ([entry]) =>
            {
                if (!entry.isIntersecting) return;
                setIsVisible(true);
                observer.unobserve(entry.target);
            },
            {threshold: 0.3},
        );

        observer.observe(section);
        return () => observer.disconnect();
    }, []);

    return (
        <section
            id="how-it-works-section"
            className={styles.section}
            aria-labelledby="how-it-works-heading"
            ref={sectionRef}
        >
            <div className={styles.container}>
                <div className={styles.intro}>
                    <Title2 id="how-it-works-heading" className={styles.title}>How it works</Title2>
                    <Text size={500} className={styles.subtitle}>
                        Move every document process from first invitation to a clear, auditable outcome.
                    </Text>
                </div>

                <div className={styles.steps}>
                    {steps.map((step, index) => (
                        <article
                            key={step.number}
                            className={mergeClasses(
                                styles.step,
                                styles[step.animationClass as keyof typeof styles],
                                isVisible && styles.stepVisible,
                            )}
                        >
                            <div
                                className={mergeClasses(
                                    styles.stepTimeline,
                                    index === steps.length - 1 && styles.lastStepTimeline,
                                    isVisible && styles.connectorVisible,
                                )}
                            >
                                <div
                                    className={mergeClasses(
                                        styles.stepMarker,
                                        index === steps.length - 1 && styles.lastStepMarker,
                                    )}
                                >
                                    <Text className={styles.number}>{step.number}</Text>
                                </div>
                            </div>
                            <div className={styles.stepCard}>
                                <div
                                    className={styles[step.illustrationClass as keyof typeof styles]}
                                    role="img"
                                    aria-label={step.illustrationLabel}
                                />
                                <Text className={styles.stepLabel}>Step {step.number}</Text>
                                <Title3 className={styles.stepTitle}>{step.title}</Title3>
                                <Text className={styles.stepDescription}>{step.description}</Text>
                            </div>
                        </article>
                    ))}
                </div>
            </div>
        </section>
    );
}
