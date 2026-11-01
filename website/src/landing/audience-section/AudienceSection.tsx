import {
    Accordion,
    AccordionHeader,
    AccordionItem,
    AccordionPanel,
    Text,
    Title2,
} from "@fluentui/react-components";
import {Add20Filled, Subtract20Filled} from "@fluentui/react-icons";
import {useState} from "react";
import {audienceGroups} from "./audienceContent.ts";
import {useAudienceSectionStyles} from "./AudienceSectionStyles.tsx";

export function AudienceSection()
{
    const styles = useAudienceSectionStyles();
    const [openItem, setOpenItem] = useState<number>(0);

    return (
        <section id={"audience-section"}
                 className={styles.wrapper}>
            <section id={"audience-section-intro"}
                     className={styles.intro}>
                <Title2 id={"audience-section-title"}
                        className={styles.sectionTitle}>
                    More industry coverage
                </Title2>
                <Text id={"audience-section-subheading"}
                      className={styles.subheading}
                      size={500}>
                    Explore additional use cases beyond the industry solutions above.
                </Text>
            </section>

            <section id={"audience-section-container"}
                     className={styles.container}>
                <Accordion id={"audience-section-accordion"}
                           collapsible
                           openItems={openItem ? [openItem] : []}
                           onToggle={(_, data) => {
                               const nextOpenItem = data.openItems[0];
                               setOpenItem(typeof nextOpenItem === "number" ? nextOpenItem : 0);
                           }}>
                    {audienceGroups.map((group, index) => {
                        const itemValue = index + 1;
                        return (
                            <AccordionItem id={`audience-section-accordion-item-${itemValue}`}
                                           key={group.title}
                                           value={itemValue}>
                                <AccordionHeader id={`audience-section-accordion-header-${itemValue}`}
                                                 size="extra-large"
                                                 className={styles.accordionHeader}
                                                 expandIcon={openItem === itemValue
                                                     ? <Subtract20Filled id={`audience-section-collapse-icon-${itemValue}`}/>
                                                     : <Add20Filled id={`audience-section-expand-icon-${itemValue}`}/>}>
                                    {group.title}
                                </AccordionHeader>
                                <AccordionPanel id={`audience-section-accordion-panel-${itemValue}`}
                                                className={styles.accordionPanel}>
                                    <ul id={`audience-section-list-${itemValue}`}
                                        className={styles.list}>
                                        {group.items.map((item, itemIndex) => (
                                            <li id={`audience-section-item-${itemValue}-${itemIndex + 1}`}
                                                key={item.title}>
                                                <Text id={`audience-section-item-title-${itemValue}-${itemIndex + 1}`}
                                                      weight="semibold">{item.title}</Text>
                                                <Text id={`audience-section-item-body-${itemValue}-${itemIndex + 1}`}
                                                      size={300}>{item.body}</Text>
                                            </li>
                                        ))}
                                    </ul>
                                </AccordionPanel>
                            </AccordionItem>
                        );
                    })}
                </Accordion>
            </section>
        </section>
    );
}
