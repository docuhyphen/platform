import React, {useState} from 'react';
import {
    Button,
    CounterBadge,
    Popover,
    PopoverSurface,
    PopoverTrigger,
    Tab,
    TabList,
} from '@fluentui/react-components';
import {useNotifications} from '../../../../context/NotificationContext';
import {NotificationsIcon} from '../../IconBundles';
import {useNotificationsPanelStyles} from './NotificationsPanelStyles';
import NotificationsTab from './notifications-tab/NotificationsTab';
import PendingApprovalsTab from './pending-approvals-tab/PendingApprovalsTab';
import {usePendingApprovals} from './pending-approvals-tab/usePendingApprovals';

type ActiveTab = 'notifications' | 'pending-approvals';

const NotificationsPanel: React.FC = () =>
{
    const styles = useNotificationsPanelStyles();
    const {unreadCount} = useNotifications();
    const pendingApprovals = usePendingApprovals();
    const [activeTab, setActiveTab] = useState<ActiveTab>('notifications');
    const hasPendingApprovals = pendingApprovals.items.length > 0;

    return (
        <Popover withArrow>
            <PopoverTrigger disableButtonEnhancement>
                <div
                    id="notifications-panel-trigger"
                    className={styles.triggerContainer}
                >
                    <Button
                        id="notifications-panel-bell-btn"
                        icon={<NotificationsIcon/>}
                        appearance="subtle"
                        shape="circular"
                        aria-label="Notifications and pending approvals"
                    />
                    {(unreadCount > 0 || hasPendingApprovals) && (
                        <span
                            id="notifications-panel-bell-dot"
                            className={styles.alertDot}
                        />
                    )}
                </div>
            </PopoverTrigger>
            <PopoverSurface>
                <div
                    id="notifications-panel-content"
                    className={styles.popoverContent}
                >
                    <TabList
                        id="notifications-panel-tabs"
                        selectedValue={activeTab}
                        onTabSelect={(_event, data) => setActiveTab(data.value as ActiveTab)}
                    >
                        <Tab
                            id="notifications-panel-tab-notifications"
                            value="notifications"
                            className={unreadCount > 0 ? styles.alertTab : undefined}
                        >
                            Notifications
                            {unreadCount > 0 && (
                                <CounterBadge
                                    id="notifications-panel-unread-count"
                                    className={styles.tabCountBadge}
                                    size="small"
                                    appearance="filled"
                                    color="danger"
                                    count={unreadCount}
                                />
                            )}
                        </Tab>
                        <Tab
                            id="notifications-panel-tab-pending-approvals"
                            value="pending-approvals"
                            className={hasPendingApprovals ? styles.alertTab : undefined}
                        >
                            Pending Approvals
                            {hasPendingApprovals && (
                                <CounterBadge
                                    id="notifications-panel-pending-count"
                                    className={styles.tabCountBadge}
                                    size="small"
                                    appearance="filled"
                                    color="informative"
                                    count={pendingApprovals.items.length}
                                />
                            )}
                        </Tab>
                    </TabList>
                    <div
                        id="notifications-panel-tab-content"
                        className={styles.tabContent}
                    >
                        {activeTab === 'notifications' && <NotificationsTab/>}
                        {activeTab === 'pending-approvals' && (
                            <PendingApprovalsTab state={pendingApprovals}/>
                        )}
                    </div>
                </div>
            </PopoverSurface>
        </Popover>
    );
};

export default NotificationsPanel;
