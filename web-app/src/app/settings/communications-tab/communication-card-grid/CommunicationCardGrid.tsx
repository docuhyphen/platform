import {CommunicationSummaryDto} from '../../../models/models';
import {CommunicationListItemContext} from '../communicationListTypes';
import CommunicationCard from '../communication-card/CommunicationCard';
import {useCommunicationCardGridStyles} from './CommunicationCardGridStyles';

interface Props
{
    communications: CommunicationSummaryDto[];
    context: CommunicationListItemContext;
}

const CommunicationCardGrid = ({communications, context}: Props) =>
{
    const styles = useCommunicationCardGridStyles();

    return (
        <div id={"communications-tab-div-4"}
             className={styles.cardGrid}>
            {communications.map(communication => (
                <CommunicationCard
                    key={communication.id}
                    communication={communication}
                    context={context}
                />
            ))}
        </div>
    );
};

export default CommunicationCardGrid;
