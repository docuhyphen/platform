import {Button} from '@fluentui/react-components';
import {DocumentAddIcon, PickFromLibraryIcon} from '../../../components/IconBundles.tsx';
import {useExchangeInitiationStyles} from '../../../exchange-initiation/ExchangeInitiationStyles.tsx';
import {BlueprintEditorForm} from '../useBlueprintEditorForm.ts';
import BlueprintDocumentCard from './blueprint-document-card/BlueprintDocumentCard.tsx';

interface BlueprintDocumentsTabProps
{
    form: BlueprintEditorForm;
}

const BlueprintDocumentsTab = ({form}: BlueprintDocumentsTabProps) =>
{
    const styles = useExchangeInitiationStyles();
    const {values, availableVariables, updateDocument, removeDocument, addDocument, setPickerOpen} = form;

    return (
        <div id={"blueprint-editor-exchange-documents-tab-content"}
             className={styles.exchangeDocumentsTabContent}>
            {values.documents.map((doc, i) => (
                <BlueprintDocumentCard key={i}
                                       index={i}
                                       document={doc}
                                       availableVariables={availableVariables}
                                       onChange={patch => updateDocument(i, patch)}
                                       onRemove={() => removeDocument(i)}/>
            ))}
            <div id={"blueprint-editor-add-document-button-container"}
                 className={styles.addDocumentButtonContainer}>
                <Button
                    id="bp-doc-add-btn"
                    onClick={addDocument}
                    shape="circular"
                    icon={<DocumentAddIcon/>}
                    appearance="subtle"
                >
                    Add Document
                </Button>
                <Button
                    id="bp-doc-pick-library-btn"
                    onClick={() => setPickerOpen(true)}
                    shape="circular"
                    icon={<PickFromLibraryIcon/>}
                    appearance="subtle"
                >
                    Pick from Library
                </Button>
            </div>
        </div>
    );
};

export default BlueprintDocumentsTab;
