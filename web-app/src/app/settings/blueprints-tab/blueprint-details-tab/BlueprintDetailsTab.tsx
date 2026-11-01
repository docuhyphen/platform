import {Accordion, AccordionHeader, AccordionItem, AccordionPanel} from '@fluentui/react-components';
import {BlueprintEditorForm} from '../useBlueprintEditorForm.ts';
import BlueprintDetailsSection from './blueprint-details-section/BlueprintDetailsSection.tsx';
import BlueprintExchangeDetailsSection
    from './blueprint-exchange-details-section/BlueprintExchangeDetailsSection.tsx';
import {useBlueprintDetailsTabStyles} from './BlueprintDetailsTabStyles.tsx';

interface BlueprintDetailsTabProps
{
    form: BlueprintEditorForm;
}

const BlueprintDetailsTab = ({form}: BlueprintDetailsTabProps) =>
{
    const styles = useBlueprintDetailsTabStyles();

    return (
        <Accordion id={"blueprint-editor-accordion"}
                   multiple
                   defaultOpenItems={['blueprint']}>
            <AccordionItem id={"blueprint-editor-accordion-item"}
                           value="blueprint">
                <AccordionHeader id={"blueprint-editor-blueprint-details-accordion-header"}>
                    Blueprint Details
                </AccordionHeader>
                <AccordionPanel id={"blueprint-editor-accordion-panel"}>
                    <div id={"blueprint-editor-div"}
                         className={styles.accordionPanelContent}>
                        <BlueprintDetailsSection form={form}/>
                    </div>
                </AccordionPanel>
            </AccordionItem>

            <AccordionItem id={"blueprint-editor-accordion-item-2"}
                           value="exchange">
                <AccordionHeader id={"blueprint-editor-exchange-details-accordion-header"}>
                    Exchange Details
                </AccordionHeader>
                <AccordionPanel id={"blueprint-editor-accordion-panel-2"}>
                    <div id={"blueprint-editor-div-2"}
                         className={styles.accordionPanelContent}>
                        <BlueprintExchangeDetailsSection form={form}/>
                    </div>
                </AccordionPanel>
            </AccordionItem>
        </Accordion>
    );
};

export default BlueprintDetailsTab;
