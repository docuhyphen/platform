import React from 'react';
import {Badge, Button, Text} from '@fluentui/react-components';
import {BlueprintDefinitionSummaryDto} from '../../../../models/models.tsx';
import {AddIcon} from "../../../../components/IconBundles.tsx";
import {useExchangeInitiationStyles} from '../../../ExchangeInitiationStyles.tsx';
import BlueprintInformationRequestBadge
    from '../../blueprint-information-request-badge/BlueprintInformationRequestBadge.tsx';

interface BlueprintCardProps
{
    blueprint: BlueprintDefinitionSummaryDto;
    onSelect: (blueprint: BlueprintDefinitionSummaryDto) => void;
}

const BlueprintCard: React.FC<BlueprintCardProps> = ({blueprint, onSelect}) =>
{
    const styles = useExchangeInitiationStyles();

    return (
        <div
            id={`blueprint-picker-blueprint-card-${blueprint.id}`}
            className={styles.blueprintCard}
        >
            <div id={`blueprint-picker-blueprint-card-header-${blueprint.id}`}
                 className={styles.blueprintCardHeader}>
                <Text id={`blueprint-picker-name-text-${blueprint.id}`}
                      weight="semibold"
                      size={400}>{blueprint.name}</Text>
                <Button
                    id={`blueprint-use-btn-${blueprint.id}`}
                    appearance="primary"
                    shape="circular"
                    size="small"
                    icon={<AddIcon/>}
                    onClick={() => onSelect(blueprint)}
                >
                    Use Blueprint
                </Button>
            </div>
            {blueprint.summary && (
                <Text id={`blueprint-picker-blueprint-card-summary-${blueprint.id}`}
                      size={200}
                      className={styles.blueprintCardSummary}>
                    {blueprint.summary}
                </Text>
            )}
            <BlueprintInformationRequestBadge blueprint={blueprint}/>
            {blueprint.generalTags.length > 0 && (
                <div id={`blueprint-picker-blueprint-tag-row-${blueprint.id}`}
                     className={styles.blueprintTagRow}>
                    {blueprint.generalTags.map(tag => (
                        <Badge id={`blueprint-picker-tag-badge-${blueprint.id}-${tag}`}
                               key={tag}
                               appearance="tint"
                               size="small">{tag}</Badge>
                    ))}
                </div>
            )}
        </div>
    );
};

export default BlueprintCard;
