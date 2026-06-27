import React, {useEffect, useState} from 'react';
import {
    Badge,
    Button,
    Checkbox,
    Spinner,
    Tab,
    TabList,
    Text,
    tokens,
} from '@fluentui/react-components';
import {DocumentLibraryEntrySummaryDto} from '../../../models/models.tsx';
import {listDocumentLibraryEntries} from '../../../../services/documentLibraryService.ts';
import {BackIcon} from '../../../components/IconBundles.tsx';

interface DocumentLibraryPickerProps
{
    onSelect: (entries: DocumentLibraryEntrySummaryDto[]) => void;
    onBack: () => void;
}

type PickerTab = 'PERSONAL' | 'ORG' | 'APP';

const tabLabel: Record<PickerTab, string> = {
    PERSONAL: 'My Documents',
    ORG: 'Organization',
    APP: 'Platform',
};

const DocumentLibraryPicker: React.FC<DocumentLibraryPickerProps> = ({onSelect, onBack}) =>
{
    const [activeTab, setActiveTab] = useState<PickerTab>('PERSONAL');
    const [entries, setEntries] = useState<DocumentLibraryEntrySummaryDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

    useEffect(() =>
    {
        setSelectedIds(new Set());
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

    const toggleEntry = (id: string) =>
    {
        setSelectedIds(prev =>
        {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
        });
    };

    const handleConfirm = () =>
    {
        const selected = entries.filter(e => selectedIds.has(e.id));
        onSelect(selected);
    };

    const count = selectedIds.size;

    return (
        <div
            id="doc-library-picker"
            style={{display: 'flex', flexDirection: 'column', gap: '12px', minHeight: '300px'}}
        >
            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
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
                    onClick={handleConfirm}
                >
                    {count > 0 ? `Add ${count} document${count === 1 ? '' : 's'}` : 'Add documents'}
                </Button>
            </div>

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
                            onClick={() => toggleEntry(entry.id)}
                            style={{
                                border: `1px solid ${selectedIds.has(entry.id) ? 'var(--colorBrandStroke1)' : 'var(--colorNeutralStroke1)'}`,
                                borderRadius: tokens.borderRadiusXLarge,
                                padding: '12px 16px',
                                display: 'flex',
                                flexDirection: 'column',
                                gap: '6px',
                                cursor: 'pointer',
                                backgroundColor: selectedIds.has(entry.id) ? 'var(--colorBrandBackground2)' : undefined,
                            }}
                        >
                            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start'}}>
                                <Text
                                    weight="semibold"
                                    size={400}
                                >
                                    {entry.title}
                                </Text>
                                <Checkbox
                                    id={`doc-picker-check-${entry.id}`}
                                    checked={selectedIds.has(entry.id)}
                                    onChange={() => toggleEntry(entry.id)}
                                    onClick={e => e.stopPropagation()}
                                />
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

        </div>
    );
};

export default DocumentLibraryPicker;
