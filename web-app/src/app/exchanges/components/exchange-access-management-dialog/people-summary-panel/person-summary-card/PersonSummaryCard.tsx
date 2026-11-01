import {ReactNode} from "react";
import {Text} from "@fluentui/react-components";
import {usePersonSummaryCardStyles} from "./PersonSummaryCardStyles.tsx";

interface PersonSummaryCardProps
{
    cardId: string;
    detailsId: string;
    nameId: string;
    secondaryId: string;
    name: string;
    secondary: string;
    badge: ReactNode;
}

const PersonSummaryCard = ({cardId, detailsId, nameId, secondaryId, name, secondary, badge}: PersonSummaryCardProps) =>
{
    const styles = usePersonSummaryCardStyles();
    return (
        <div
            id={cardId}
            className={styles.personCard}
        >
            <div id={detailsId}
                 className={styles.personDetails}>
                <Text id={nameId}
                      weight={"semibold"}>{name}</Text>
                <Text
                    id={secondaryId}
                    size={200}
                    className={styles.secondaryText}
                >
                    {secondary}
                </Text>
            </div>
            {badge}
        </div>
    );
};

export default PersonSummaryCard;
