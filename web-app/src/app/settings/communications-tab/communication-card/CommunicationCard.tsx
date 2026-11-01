import {Badge, Text} from '@fluentui/react-components';
import {CommunicationSummaryDto} from '../../../models/models';
import TagList from '../../../components/TagList.tsx';
import {CommunicationListItemContext} from '../communicationListTypes';
import CommunicationActionsMenu from '../communication-actions-menu/CommunicationActionsMenu';
import {useCommunicationCardStyles} from './CommunicationCardStyles';

interface Props
{
    communication: CommunicationSummaryDto;
    context: CommunicationListItemContext;
}

const CommunicationCard = ({communication, context}: Props) =>
{
    const styles = useCommunicationCardStyles();

    return (
        <div
            id={`communications-tab-div-5-${communication.id}`}
            className={styles.commCard}
        >
            <div id={"communications-tab-div-6"}
                 className={styles.commCardContent}>
                <Text id={"communications-tab-text-4"}
                      weight="semibold"
                      size={400}>{communication.name}</Text>
                {communication.summary && (
                    <Text id={"communications-tab-text-5"}
                          size={200}
                          className={styles.summaryText}>
                        {communication.summary}
                    </Text>
                )}
                <Text
                    id={"communications-tab-text-6"}
                    size={200}
                    className={styles.subjectText}
                >
                    {communication.subject}
                </Text>
                <div id={"communications-tab-div-7"}
                     className={styles.badgeRow}>
                    {context.showPublication && (
                        <Badge
                            id={"communications-tab-badge-1"}
                            appearance="tint"
                            color={communication.isPublished ? 'success' : 'warning'}
                            size="small"
                        >
                            {communication.isPublished ? 'Published' : 'Draft'}
                        </Badge>
                    )}
                    <Badge
                        id={"communications-tab-badge-2"}
                        appearance="tint"
                        color={communication.isActive ? 'success' : 'warning'}
                        size="small"
                    >
                        {communication.isActive ? 'Active' : 'Inactive'}
                    </Badge>
                    <TagList tags={communication.generalTags}/>
                </div>
            </div>
            {context.canManageItem(communication) && (
                <CommunicationActionsMenu
                    communication={communication}
                    variant={'card'}
                    showPublication={context.showPublication}
                    actions={context.actions}
                />
            )}
        </div>
    );
};

export default CommunicationCard;
