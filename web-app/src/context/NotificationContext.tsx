import React, {createContext, useContext} from 'react';
import GlobalRealtimeToast from '../app/components/global-realtime-toast/GlobalRealtimeToast';
import {NotificationInboxState, useNotificationInbox} from './useNotificationInbox';

const NotificationContext = createContext<NotificationInboxState>({
    notifications: [],
    unreadCount: 0,
    hasMoreNotifications: false,
    isLoadingMoreNotifications: false,
    loadMoreNotifications: async () => {},
    markAsRead: () => {},
    markAllAsRead: () => {},
    markMatchingAsRead: () => {},
});

export const NotificationProvider: React.FC<{children: React.ReactNode}> = ({children}) =>
{
    const {state, probeToast} = useNotificationInbox();
    return (
        <NotificationContext.Provider value={state}>
            {children}
            <GlobalRealtimeToast event={probeToast}/>
        </NotificationContext.Provider>
    );
};

export const useNotifications = (): NotificationInboxState => useContext(NotificationContext);
