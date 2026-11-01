import {CommunicationSummaryDto} from '../../../models/models';
import {CommunicationListItemContext} from '../communicationListTypes';
import CommunicationTableRow from './communication-table-row/CommunicationTableRow';
import {useCommunicationTableStyles} from './CommunicationTableStyles';

interface Props
{
    communications: CommunicationSummaryDto[];
    context: CommunicationListItemContext;
}

const CommunicationTable = ({communications, context}: Props) =>
{
    const styles = useCommunicationTableStyles();

    return (
        <table id={"communications-tab-table-1"}
               className={styles.table}>
            <thead id={"communications-tab-thead-1"}>
                <tr id={"communications-tab-tr-1"}>
                    <th id={"communications-tab-th-1"}
                        className={styles.th}>Name</th>
                    <th id={"communications-tab-th-2"}
                        className={styles.th}>Subject</th>
                    <th id={"communications-tab-th-3"}
                        className={styles.th}>Tags</th>
                    {context.showPublication && <th id={"communications-tab-th-4"}
                                                    className={styles.th}>Published</th>}
                    <th id={"communications-tab-th-5"}
                        className={styles.th}>Active</th>
                    <th id={"communications-tab-th-6"}
                        className={styles.th}/>
                </tr>
            </thead>
            <tbody id={"communications-tab-tbody-1"}>
                {communications.map(communication => (
                    <CommunicationTableRow
                        key={communication.id}
                        communication={communication}
                        context={context}
                    />
                ))}
            </tbody>
        </table>
    );
};

export default CommunicationTable;
