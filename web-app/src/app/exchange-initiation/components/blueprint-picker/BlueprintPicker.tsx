import React, {useEffect, useState} from 'react';
import {
    Badge,
    Button,
    Spinner,
    Tab,
    TabList,
    Text,
} from '@fluentui/react-components';
import {BlueprintDefinitionSummaryDto} from '../../../models/models.tsx';
import {listBlueprints} from '../../../../services/blueprintService.ts';
import {AddIcon} from "../../../components/IconBundles.tsx";
import {useExchangeInitiationStyles} from '../../ExchangeInitiationStyles.tsx';

interface BlueprintPickerProps
{
    onSelect: (blueprint: BlueprintDefinitionSummaryDto) => void;
    onCancel: () => void;
}

type PickerTab = 'PERSONAL' | 'ORG' | 'APP';

const BlueprintPicker: React.FC<BlueprintPickerProps> = ({onSelect, onCancel}) =>
{
    const styles = useExchangeInitiationStyles();
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
        <div className={styles.blueprintPickerContainer}>
            <TabList
                selectedValue={activeTab}
                onTabSelect={(_, data) => setActiveTab(data.value as PickerTab)}
            >
                <Tab value="PERSONAL">{tabLabel.PERSONAL}</Tab>
                <Tab value="ORG">{tabLabel.ORG}</Tab>
                <Tab value="APP">{tabLabel.APP}</Tab>
            </TabList>

            {loading && (
                <div className={styles.blueprintPickerSpinnerWrapper}>
                    <Spinner size="small" label="Loading blueprints..."/>
                </div>
            )}

            {!loading && error && (
                <Text className={styles.blueprintPickerErrorText}>{error}</Text>
            )}

            {!loading && !error && blueprints.length === 0 && (
                <Text className={styles.blueprintPickerEmptyText}>
                    No blueprints available in this category.
                </Text>
            )}

            {!loading && !error && blueprints.length > 0 && (
                <div className={styles.blueprintList}>
                    {blueprints.map(bp => (
                        <div
                            key={bp.id}
                            className={styles.blueprintCard}
                        >
                            <div className={styles.blueprintCardHeader}>
                                <Text weight="semibold" size={400}>{bp.name}</Text>
                                <Button
                                    id={`blueprint-use-btn-${bp.id}`}
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
                                <Text size={200} className={styles.blueprintCardSummary}>
                                    {bp.summary}
                                </Text>
                            )}
                            {bp.generalTags.length > 0 && (
                                <div className={styles.blueprintTagRow}>
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
