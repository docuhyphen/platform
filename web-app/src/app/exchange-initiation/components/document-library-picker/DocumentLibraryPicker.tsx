import React, {useEffect, useState} from 'react';
import {
    Badge,
    Button,
    Spinner,
    Tab,
    TabList,
    Text,
} from '@fluentui/react-components';
import {DocumentLibraryEntrySummaryDto} from '../../../models/models.tsx';
import {listDocumentLibraryEntries} from '../../../../services/documentLibraryService.ts';

interface DocumentLibraryPickerProps
{
    onSelect: (entry: DocumentLibraryEntrySummaryDto) => void;
    onCancel: () => void;
}

type PickerTab = 'PERSONAL' | 'ORG' | 'APP';

const tabLabel: Record<PickerTab, string> = {
    PERSONAL: 'My Documents',
    ORG: 'Organization',
    APP: 'Platform',
};

const DocumentLibraryPicker: React.FC<DocumentLibraryPickerProps> = ({onSelect, onCancel}) =>
{
    const [activeTab, setActiveTab] = useState<PickerTab>('PERSONAL');
    const [entries, setEntries] = useState<DocumentLibraryEntrySummaryDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() =>
    {
        setLoading(true);
        setError(null);
        listDocumentLibraryEntries({scope: activeTab})
            .then(all =>
            {
                const visible = all.filter(e =>
                    e.hasFile && e.isActive && (e.scope === 'PERSONAL' || e.isPublished),
                );
                setEntries(visible);
            })
            .catch(() => setError('Failed to load documents'))
            .finally(() => setLoading(false));
    }, [activeTab]);

    return (
        <div
            id="doc-library-picker"
            style={{display: 'flex', flexDirection: 'column', gap: '12px', minHeight: '300px'}}
        >
            <TabList
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

            {loading && (
                <div style={{display: 'flex', justifyContent: 'center', padding: '24px'}}>
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
                    style={{color: 'var(--colorPaletteRedForeground1)'}}
                >
                    {error}
                </Text>
            )}

            {!loading && !error && entries.length === 0 && (
                <Text
                    id="doc-picker-empty"
                    style={{color: 'var(--colorNeutralForeground3)'}}
                >
                    No documents available in this category.
                </Text>
            )}

            {!loading && !error && entries.length > 0 && (
                <div style={{display: 'flex', flexDirection: 'column', gap: '8px', overflowY: 'auto'}}>
                    {entries.map(entry => (
                        <div
                            key={entry.id}
                            id={`doc-picker-item-${entry.id}`}
                            style={{
                                border: '1px solid var(--colorNeutralStroke1)',
                                borderRadius: '8px',
                                padding: '12px 16px',
                                display: 'flex',
                                flexDirection: 'column',
                                gap: '6px',
                            }}
                        >
                            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start'}}>
                                <Text
                                    weight="semibold"
                                    size={400}
                                >
                                    {entry.title}
                                </Text>
                                <Button
                                    id={`doc-picker-select-${entry.id}`}
                                    appearance="primary"
                                    shape="circular"
                                    size="small"
                                    onClick={() => onSelect(entry)}
                                >
                                    Select
                                </Button>
                            </div>
                            {entry.description && (
                                <Text
                                    size={200}
                                    style={{color: 'var(--colorNeutralForeground2)'}}
                                >
                                    {entry.description}
                                </Text>
                            )}
                            <div style={{display: 'flex', gap: '4px', flexWrap: 'wrap'}}>
                                {entry.documentType && (
                                    <Badge
                                        appearance="tint"
                                        color="informative"
                                        size="small"
                                    >
                                        {entry.documentType}
                                    </Badge>
                                )}
                                {entry.generalTags.map(tag => (
                                    <Badge
                                        key={tag}
                                        appearance="tint"
                                        size="small"
                                    >
                                        {tag}
                                    </Badge>
                                ))}
                            </div>
                        </div>
                    ))}
                </div>
            )}

            <div style={{display: 'flex', justifyContent: 'flex-end', marginTop: '8px'}}>
                <Button
                    id="doc-picker-cancel-btn"
                    appearance="secondary"
                    shape="circular"
                    onClick={onCancel}
                >
                    Cancel
                </Button>
            </div>
        </div>
    );
};

export default DocumentLibraryPicker;
