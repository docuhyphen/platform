import {Divider, Text, Title2} from "@fluentui/react-components";
import {useState} from "react";
import {RiskCard} from "./risk-card/RiskCard.tsx";
import {risks} from "./risksContent.ts";
import {useRisksSectionStyles} from "./RisksSectionStyles.tsx";

export function RisksSection()
{
    const styles = useRisksSectionStyles();
    const [expandedCards, setExpandedCards] = useState<Record<number, boolean>>({});

    const toggleCard = (riskId: number) =>
    {
        setExpandedCards((prev) => ({
            ...prev,
            [riskId]: !prev[riskId],
        }));
    };

    return (
        <>
            <section id={"risks-section"}
                     className={styles.sectionWrapper}>
                <div id={"risks-section-intro-wrapper"}
                     className={styles.introWrapper}>
                    <div id={"risks-section-intro"}
                         className={styles.intro}>
                        <Title2 id={"risks-section-title"}
                                align="start"
                                className={styles.sectionTitle}>
                            Did you know?
                        </Title2>
                        <Text id={"risks-section-subheading"}
                              size={500}
                              className={styles.subheading}>
                            The hidden risks of everyday document sharing <strong id={"risks-section-subheading-strong"}><i id={"risks-section-subheading-emphasis"}>in business</i></strong>
                        </Text>
                    </div>
                </div>

                <section id={"risks-section-cards"}
                         className={styles.cardsWrapper}>
                    <div id={"risks-section-cards-container"}
                         className={styles.cardsContainer}>
                        {risks.map((risk) => (
                            <RiskCard key={risk.id}
                                      risk={risk}
                                      isExpanded={!!expandedCards[risk.id]}
                                      onToggle={() => toggleCard(risk.id)}/>
                        ))}
                    </div>
                </section>
            </section>
            <Divider id={"risks-section-divider"}/>
        </>
    );
}
