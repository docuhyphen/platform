import React, {useEffect, useState} from 'react';
import {
    Badge,
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Input,
    Spinner,
    Tab,
    TabList,
    Text,
    tokens,
} from '@fluentui/react-components';
import {SearchRegular} from '@fluentui/react-icons';
import {CommunicationSummaryDto} from '../../models/models';
import {listCommunications} from '../../../services/communicationService';

interface Props
{
    open: boolean;
    onClose: () => void;
    onSelect: (communication: CommunicationSummaryDto) => void;
    selectedId?: string;
}

type ScopeTab = 'PERSONAL' | 'ORG' | 'PLATFORM';

const scopeLabels: Record<ScopeTab, string> = {
    PERSONAL: 'My Communications',
    ORG: 'Organization',
    PLATFORM: 'Platform',
};

const scopeColor: Record<ScopeTab, 'brand' | 'success' | 'informative'> = {
    PERSONAL: 'brand',
    ORG: 'success',
    PLATFORM: 'informative',
};

const CommunicationPickerDialog: React.FC<Props> = ({open, onClose, onSelect, selectedId}) =>
{
    const [activeTab, setActiveTab] = useState<ScopeTab>('PERSONAL');
    const [communications, setCommunications] = useState<CommunicationSummaryDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [search, setSearch] = useState('');
    const [pending, setPending] = useState<CommunicationSummaryDto | null>(null);

    useEffect(() =>
    {
        if (!open) return;
        setSearch('');
        setPending(null);
        loadCommunications(activeTab);
    }, [open]);

    useEffect(() =>
    {
        if (open) loadCommunications(activeTab);
    }, [activeTab]);

    const loadCommunications = (scope: ScopeTab) =>
    {
        setLoading(true);
        listCommunications({scope})
            .then(list => setCommunications(list.filter(t => t.isActive)))
            .catch(() => setCommunications([]))
            .finally(() => setLoading(false));
    };

    const filtered = communications.filter(t =>
        t.name.toLowerCase().includes(search.toLowerCase()) ||
        t.subject.toLowerCase().includes(search.toLowerCase()) ||
        (t.summary ?? '').toLowerCase().includes(search.toLowerCase())
    );

    const handleConfirm = () =>
    {
        if (pending) { onSelect(pending); onClose(); }
    };

    return (
        <Dialog open={open} onOpenChange={(_, {open: isOpen}) => { if (!isOpen) onClose(); }}>
            <DialogSurface style={{maxWidth: '600px', width: '100%'}}>
                <DialogBody>
                    <DialogTitle>Select Communication</DialogTitle>
                    <DialogContent>
                        <div style={{display: 'flex', flexDirection: 'column', gap: '12px'}}>
                            <TabList
                                selectedValue={activeTab}
                                onTabSelect={(_, d) =>
                                {
                                    setActiveTab(d.value as ScopeTab);
                                    setCommunications([]);
                                    setPending(null);
                                }}
                            >
                                <Tab value="PERSONAL">{scopeLabels.PERSONAL}</Tab>
                                <Tab value="ORG">{scopeLabels.ORG}</Tab>
                                <Tab value="PLATFORM">{scopeLabels.PLATFORM}</Tab>
                            </TabList>

                            <Input
                                contentBefore={<SearchRegular/>}
                                placeholder="Search communications…"
                                value={search}
                                onChange={(_, d) => setSearch(d.value)}
                            />

                            {loading && <Spinner size="small"/>}
                            {!loading && filtered.length === 0 && (
                                <Text style={{color: 'var(--colorNeutralForeground3)'}}>No communications found.</Text>
                            )}
                            <div style={{display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '360px', overflowY: 'auto'}}>
                                {!loading && filtered.map(t =>
                                {
                                    const isSelected = pending?.id === t.id || (!pending && selectedId === t.id);
                                    return (
                                        <div
                                            key={t.id}
                                            onClick={() => setPending(t)}
                                            style={{
                                                border: `2px solid ${isSelected ? 'var(--colorBrandStroke1)' : 'var(--colorNeutralStroke1)'}`,
                                                borderRadius: tokens.borderRadiusXLarge,
                                                padding: '10px 14px',
                                                cursor: 'pointer',
                                                background: isSelected ? 'var(--colorBrandBackground2)' : 'transparent',
                                                display: 'flex',
                                                flexDirection: 'column',
                                                gap: '4px',
                                            }}
                                        >
                                            <div style={{display: 'flex', alignItems: 'center', gap: '8px'}}>
                                                <Text weight="semibold" size={300}>{t.name}</Text>
                                                <Badge
                                                    appearance="tint"
                                                    color={scopeColor[t.scope as ScopeTab] ?? 'informative'}
                                                    size="small"
                                                >
                                                    {t.scope}
                                                </Badge>
                                            </div>
                                            <Text
                                                size={200}
                                                style={{
                                                    color: 'var(--colorNeutralForeground3)',
                                                    fontStyle: 'italic',
                                                    overflow: 'hidden',
                                                    textOverflow: 'ellipsis',
                                                    whiteSpace: 'nowrap',
                                                }}
                                            >
                                                {t.subject}
                                            </Text>
                                            {t.summary && (
                                                <Text size={200} style={{color: 'var(--colorNeutralForeground2)'}}>
                                                    {t.summary}
                                                </Text>
                                            )}
                                        </div>
                                    );
                                })}
                            </div>
                        </div>
                    </DialogContent>
                    <DialogActions>
                        <Button
                            appearance="primary"
                            shape="circular"
                            onClick={handleConfirm}
                            disabled={!pending}
                        >
                            Select Communication
                        </Button>
                        <Button shape="circular" onClick={onClose}>Cancel</Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default CommunicationPickerDialog;
