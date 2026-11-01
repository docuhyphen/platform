import {Badge, Button, Text, mergeClasses} from "@fluentui/react-components";
import type {RiskItem} from "../risksContent.ts";
import {useRiskCardStyles} from "./RiskCardStyles.tsx";

interface RiskCardProps
{
    risk: RiskItem;
    isExpanded: boolean;
    onToggle: () => void;
}

export function RiskCard({risk, isExpanded, onToggle}: RiskCardProps)
{
    const styles = useRiskCardStyles();

    return (
        <div id={`risk-card-${risk.id}`}
             className={mergeClasses(styles.card, styles[risk.variantClass])}>
            <div id={`risk-card-content-${risk.id}`}
                 className={styles.cardContent}>
                <Text id={`risk-card-subtitle-${risk.id}`}
                      className={styles.cardSubtitle}
                      weight="semibold">
                    <Badge id={`risk-card-badge-${risk.id}`}
                           size="large"
                           appearance="filled">{risk.id}</Badge>
                    {risk.subtitle}
                </Text>
                <Text id={`risk-card-title-${risk.id}`}
                      weight="bold"
                      className={styles.cardTitle}>{risk.title}</Text>

                <Button id={`risk-card-toggle-${risk.id}`}
                        appearance="transparent"
                        shape="circular"
                        className={styles.readMoreButton}
                        onClick={onToggle}>
                    {isExpanded ? "Read less" : "Read more"}
                </Button>

                {isExpanded && (
                    <Text id={`risk-card-body-${risk.id}`}
                          className={styles.cardParagraph}>{risk.body}</Text>
                )}
            </div>
        </div>
    );
}
