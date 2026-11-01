import {Badge, Text} from '@fluentui/react-components';
import {CommunicationSummaryDto} from '../../../../models/models';
import TagList from '../../../../components/TagList.tsx';
import {CommunicationListItemContext} from '../../communicationListTypes';
import CommunicationActionsMenu from '../../communication-actions-menu/CommunicationActionsMenu';
import {useCommunicationTableRowStyles} from './CommunicationTableRowStyles';

interface Props
{
    communication: CommunicationSummaryDto;
    context: CommunicationListItemContext;
}

const CommunicationTableRow = ({communication, context}: Props) =>
{
    const styles = useCommunicationTableRowStyles();

    return (
        <tr id={`communications-tab-tr-2-${communication.id}`}
            className={styles.tr}>
            <td id={"communications-tab-td-1"}
                className={styles.td}>
                <Text id={"communications-tab-text-7"}
                      weight="semibold">{communication.name}</Text>
                {communication.summary && <Text id={"communications-tab-text-8"}
                                                size={200}
                                                className={styles.summaryText}
                                                block>{communication.summary}</Text>}
            </td>
            <td id={"communications-tab-td-2"}
                className={styles.td}><Text id={"communications-tab-text-9"}
                                            size={200}>{communication.subject}</Text></td>
            <td id={"communications-tab-td-3"}
                className={styles.td}>
                <TagList tags={communication.generalTags}/>
            </td>
            {context.showPublication && (
                <td id={"communications-tab-td-4"}
                    className={styles.td}><Text id={"communications-tab-text-10"}
                                                size={200}>{communication.isPublished ? 'Published' : 'Draft'}</Text></td>
            )}
            <td id={"communications-tab-td-5"}
                className={styles.td}>
                <Badge id={"communications-tab-badge-3"}
                       appearance="tint"
                       color={communication.isActive ? 'success' : 'warning'}
                       size="small">
                    {communication.isActive ? 'Active' : 'Inactive'}
                </Badge>
            </td>
            <td id={"communications-tab-td-6"}
                className={styles.td}>
                {context.canManageItem(communication) && (
                    <CommunicationActionsMenu
                        communication={communication}
                        variant={'table'}
                        showPublication={context.showPublication}
                        actions={context.actions}
                    />
                )}
            </td>
        </tr>
    );
};

export default CommunicationTableRow;
