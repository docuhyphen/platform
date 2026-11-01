import React from 'react';
import {Text} from '@fluentui/react-components';
import EmptyStateIllustration from '../empty-state-illustration/EmptyStateIllustration.tsx';
import {useExchangeEmptyDetailsFrameStyles} from './ExchangeEmptyDetailsFrameStyles.tsx';

interface ExchangeEmptyDetailsFrameProps
{
    sectionId: string;
    contentId: string;
    titleId: string;
    title: string;
    children: React.ReactNode;
}

const ExchangeEmptyDetailsFrame: React.FC<ExchangeEmptyDetailsFrameProps> = (
    {
        sectionId,
        contentId,
        titleId,
        title,
        children,
    }) =>
{
    const styles = useExchangeEmptyDetailsFrameStyles();

    return (
        <div id={sectionId}
             className={styles.noExchangeSelectedSection}>
            <div id={contentId}
                 className={styles.inboxEmptyDetailsContent}>
                <EmptyStateIllustration className={styles.inboxEmptyIllustration}/>
                <Text id={titleId}
                      size={600}
                      weight={"semibold"}
                      align={"center"}>{title}</Text>
                {children}
            </div>
        </div>
    );
};

export default ExchangeEmptyDetailsFrame;
