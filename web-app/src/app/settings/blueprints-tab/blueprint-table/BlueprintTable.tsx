import {BlueprintDefinitionSummaryDto} from '../../../models/models.tsx';
import {BlueprintListItemContext} from '../blueprintListTypes.ts';
import BlueprintTableRow from './blueprint-table-row/BlueprintTableRow.tsx';
import {useBlueprintTableStyles} from './BlueprintTableStyles.tsx';

interface BlueprintTableProps
{
    blueprints: BlueprintDefinitionSummaryDto[];
    context: BlueprintListItemContext;
}

const BlueprintTable = ({blueprints, context}: BlueprintTableProps) =>
{
    const styles = useBlueprintTableStyles();

    return (
        <table id={"blueprints-tab-table-1"}
               className={styles.table}>
            <thead id={"blueprints-tab-thead-1"}>
                <tr id={"blueprints-tab-tr-1"}>
                    <th id={"blueprints-tab-th-1"}
                        className={styles.th}>Name</th>
                    <th id={"blueprints-tab-th-2"}
                        className={styles.th}>Summary</th>
                    <th id={"blueprints-tab-th-3"}
                        className={styles.th}>Tags</th>
                    {context.activeTab !== 'PERSONAL' && <th id={"blueprints-tab-th-4"}
                                                             className={styles.th}>Published</th>}
                    <th id={"blueprints-tab-th-5"}
                        className={styles.th}>Active</th>
                    <th id={"blueprints-tab-th-6"}
                        className={styles.th}/>
                </tr>
            </thead>
            <tbody id={"blueprints-tab-tbody-1"}>
                {blueprints.map(bp => (
                    <BlueprintTableRow key={bp.id}
                                       blueprint={bp}
                                       context={context}/>
                ))}
            </tbody>
        </table>
    );
};

export default BlueprintTable;
