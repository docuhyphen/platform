import React, {ReactNode} from 'react';
import {AccordionHeader, AccordionItem, AccordionPanel, Text} from '@fluentui/react-components';
import {useHelpGuideSectionStyles} from './HelpGuideSectionStyles.tsx';

interface HelpGuideSectionProps
{
    index: number;
    value: string;
    title: string;
    sectionId: string;
    introId?: string;
    intro?: ReactNode;
    children: ReactNode;
}

const HelpGuideSection: React.FC<HelpGuideSectionProps> = ({index, value, title, sectionId, introId, intro, children}) =>
{
    const styles = useHelpGuideSectionStyles();
    return (
        <AccordionItem id={`manage-access-help-guide-accordion-item-${index}`}
                       value={value}>
            <AccordionHeader id={`manage-access-help-guide-accordion-header-${index}`}>{title}</AccordionHeader>
            <AccordionPanel id={`manage-access-help-guide-accordion-panel-${index}`}>
                <div id={sectionId}
                     className={styles.section}>
                    {intro && (
                        <Text id={introId}
                              size={200}
                              className={styles.sectionIntro}>
                            {intro}
                        </Text>
                    )}
                    {children}
                </div>
            </AccordionPanel>
        </AccordionItem>
    );
};

export default HelpGuideSection;
