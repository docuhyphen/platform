import React, {useEffect, useState} from 'react';
import {
    Badge,
    Button,
    Spinner,
    Tab,
    TabList,
    Text, tokens,
} from '@fluentui/react-components';
import {BlueprintDefinitionSummaryDto} from '../../../models/models.tsx';
import {listBlueprints} from '../../../../services/blueprintService.ts';
import {AddIcon} from "../../../components/IconBundles.tsx";

interface BlueprintPickerProps
{
    onSelect: (blueprint: BlueprintDefinitionSummaryDto) => void;
    onCancel: () => void;
}

type PickerTab = 'PERSONAL' | 'ORG' | 'APP';

const BlueprintPicker: React.FC<BlueprintPickerProps> = ({onSelect, onCancel}) =>
{
    const [activeTab, setActiveTab] = useState<PickerTab>('PERSONAL');
    const [blueprints, setBlueprints] = useState<BlueprintDefinitionSummaryDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() =>
    {
        setLoading(true);
        setError(null);
        listBlueprints({scope: activeTab})
            .then(all =>
            {
                // Picker only shows blueprints that are ready to use:
                // active for all scopes; published for ORG/APP (PERSONAL has no publish concept).
                const visible = all.filter(bp =>
                    bp.isActive && (bp.scope === 'PERSONAL' || bp.isPublished),
                );
                setBlueprints(visible);
            })
            .catch(() => setError('Failed to load blueprints'))
            .finally(() => setLoading(false));
    }, [activeTab]);

    const tabLabel: Record<PickerTab, string> = {
        PERSONAL: 'My Blueprints',
        ORG: 'Organization',
        APP: 'Platform',
    };

    return (
        <div style={{display: 'flex', flexDirection: 'column', gap: '12px', minHeight: '300px'}}>
            <TabList
                selectedValue={activeTab}
                onTabSelect={(_, data) => setActiveTab(data.value as PickerTab)}
            >
                <Tab value="PERSONAL">{tabLabel.PERSONAL}</Tab>
                <Tab value="ORG">{tabLabel.ORG}</Tab>
                <Tab value="APP">{tabLabel.APP}</Tab>
            </TabList>

            {loading && (
                <div style={{display: 'flex', justifyContent: 'center', padding: '24px'}}>
                    <Spinner size="small" label="Loading blueprints…"/>
                </div>
            )}

            {!loading && error && (
                <Text style={{color: 'var(--colorPaletteRedForeground1)'}}>{error}</Text>
            )}

            {!loading && !error && blueprints.length === 0 && (
                <Text style={{color: 'var(--colorNeutralForeground3)'}}>
                    No blueprints available in this category.
                </Text>
            )}

            {!loading && !error && blueprints.length > 0 && (
                <div style={{display: 'flex', flexDirection: 'column', gap: '8px', overflowY: 'auto'}}>
                    {blueprints.map(bp => (
                        <div
                            key={bp.id}
                            style={{
                                border: '1px solid var(--colorNeutralStroke1)',
                                borderRadius: tokens.borderRadiusLarge,
                                padding: '12px 16px',
                                display: 'flex',
                                flexDirection: 'column',
                                gap: '6px',
                            }}
                        >
                            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start'}}>
                                <Text weight="semibold" size={400}>{bp.name}</Text>
                                <Button
                                    appearance="primary"
                                    shape="circular"
                                    size="small"
                                    icon={<AddIcon/>}
                                    onClick={() => onSelect(bp)}
                                >
                                    Use Blueprint
                                </Button>
                            </div>
                            {bp.summary && (
                                <Text size={200} style={{color: 'var(--colorNeutralForeground2)'}}>
                                    {bp.summary}
                                </Text>
                            )}
                            {bp.generalTags.length > 0 && (
                                <div style={{display: 'flex', gap: '4px', flexWrap: 'wrap'}}>
                                    {bp.generalTags.map(tag => (
                                        <Badge key={tag} appearance="tint" size="small">{tag}</Badge>
                                    ))}
                                </div>
                            )}
                            {!bp.isActive && (
                                <Badge appearance="tint" color="warning" size="small">Inactive</Badge>
                            )}
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};

export default BlueprintPicker;
