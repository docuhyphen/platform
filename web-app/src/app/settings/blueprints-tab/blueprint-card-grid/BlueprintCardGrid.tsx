import {BlueprintDefinitionSummaryDto} from '../../../models/models.tsx';
import {BlueprintListItemContext} from '../blueprintListTypes.ts';
import BlueprintCard from '../blueprint-card/BlueprintCard.tsx';
import {useBlueprintCardGridStyles} from './BlueprintCardGridStyles.tsx';

interface BlueprintCardGridProps
{
    blueprints: BlueprintDefinitionSummaryDto[];
    context: BlueprintListItemContext;
}

const BlueprintCardGrid = ({blueprints, context}: BlueprintCardGridProps) =>
{
    const styles = useBlueprintCardGridStyles();

    return (
        <div id={"blueprints-tab-div-9"}
             className={styles.cardGrid}>
            {blueprints.map(bp => (
                <BlueprintCard key={bp.id}
                               blueprint={bp}
                               context={context}/>
            ))}
        </div>
    );
};

export default BlueprintCardGrid;
