import React from 'react';
import {Button, Spinner, Tab, TabList, Text} from '@fluentui/react-components';
import {DocumentLibraryEntrySummaryDto} from '../../../models/models.tsx';
import {BackIcon} from '../../../components/IconBundles.tsx';
import {useExchangeInitiationStyles} from '../../ExchangeInitiationStyles.tsx';
import {PickerTab, tabLabel} from './documentLibraryPickerTypes.ts';
import {useDocumentLibraryPicker} from './useDocumentLibraryPicker.ts';
import DocumentLibraryPickerEntry from './document-library-picker-entry/DocumentLibraryPickerEntry.tsx';

interface DocumentLibraryPickerProps
{
    onSelect: (entries: DocumentLibraryEntrySummaryDto[]) => void;
    onBack: () => void;
    enforcedScope?: PickerTab;
}

const DocumentLibraryPicker: React.FC<DocumentLibraryPickerProps> = ({onSelect, onBack, enforcedScope}) =>
{
    const styles = useExchangeInitiationStyles();
    const {
        activeTab,
        setActiveTab,
        entries,
        loading,
        error,
        selectedIds,
        toggleEntry,
        selectedEntries,
    } = useDocumentLibraryPicker(enforcedScope);

    const count = selectedIds.size;

    return (
        <div
            id="doc-library-picker"
            className={styles.docPickerContainer}
        >
            <div id={"document-library-picker-div-1"}
                 className={styles.docPickerHeader}>
                <Button
                    id="doc-picker-back-btn"
                    appearance="subtle"
                    shape="circular"
                    icon={<BackIcon/>}
                    onClick={onBack}
                >
                    Back to Documents
                </Button>
                <Button
                    id="doc-picker-confirm-btn"
                    appearance="primary"
                    shape="circular"
                    disabled={count === 0}
                    onClick={() => onSelect(selectedEntries())}
                >
                    {count > 0 ? `Add ${count} document${count === 1 ? '' : 's'}` : 'Add documents'}
                </Button>
            </div>

            {!enforcedScope && (
                <TabList
                    id={"document-library-picker-tab-list-1"}
                    selectedValue={activeTab}
                    onTabSelect={(_, data) => setActiveTab(data.value as PickerTab)}
                >
                    <Tab
                        id="doc-picker-tab-personal"
                        value="PERSONAL"
                    >
                        {tabLabel.PERSONAL}
                    </Tab>
                    <Tab
                        id="doc-picker-tab-org"
                        value="ORG"
                    >
                        {tabLabel.ORG}
                    </Tab>
                    <Tab
                        id="doc-picker-tab-app"
                        value="APP"
                    >
                        {tabLabel.APP}
                    </Tab>
                </TabList>
            )}

            {loading && (
                <div id={"document-library-picker-div-2"}
                     className={styles.docPickerSpinnerWrapper}>
                    <Spinner
                        id="doc-picker-spinner"
                        size="medium"
                        label="Loading documents..."
                    />
                </div>
            )}

            {!loading && error && (
                <Text
                    id="doc-picker-error"
                    className={styles.docPickerErrorText}
                >
                    {error}
                </Text>
            )}

            {!loading && !error && entries.length === 0 && (
                <Text
                    id="doc-picker-empty"
                    className={styles.docPickerEmptyText}
                >
                    No documents available in this category.
                </Text>
            )}

            {!loading && !error && entries.length > 0 && (
                <div id={"document-library-picker-div-3"}
                     className={styles.docPickerList}>
                    {entries.map(entry => (
                        <DocumentLibraryPickerEntry
                            key={entry.id}
                            entry={entry}
                            selected={selectedIds.has(entry.id)}
                            onToggle={toggleEntry}
                        />
                    ))}
                </div>
            )}

        </div>
    );
};

export default DocumentLibraryPicker;
