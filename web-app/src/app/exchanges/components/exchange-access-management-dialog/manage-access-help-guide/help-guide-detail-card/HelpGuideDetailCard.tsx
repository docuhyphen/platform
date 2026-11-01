import React from 'react';
import {Text} from '@fluentui/react-components';
import {HelpGuideDetailContent} from '../manageAccessHelpGuideContent.ts';
import {useHelpGuideDetailCardStyles} from './HelpGuideDetailCardStyles.tsx';

interface HelpGuideDetailCardProps
{
    card: HelpGuideDetailContent;
}

const HelpGuideDetailCard: React.FC<HelpGuideDetailCardProps> = ({card}) =>
{
    const styles = useHelpGuideDetailCardStyles();
    return (
        <div id={card.cardId}
             className={styles.constraintCard}>
            <Text id={card.titleId}
                  weight="semibold"
                  size={300}>{card.title}</Text>
            <Text id={card.textId}
                  size={200}>
                {card.description}
            </Text>
        </div>
    );
};

export default HelpGuideDetailCard;
