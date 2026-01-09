import React, {createContext, useContext, useEffect, useState} from 'react';
import {NotificationDto} from '../app/models/models';
import {useAuth} from "./AuthContext.tsx";
import {notificationService} from "../services/NotificationService.tsx";

interface NotificationContextType
{
    notifications: NotificationDto[];
    unreadCount: number;
    markAsRead: (notificationId: string) => void;
    markAllAsRead: () => void;
}

const NotificationContext = createContext<NotificationContextType>(
    {
        notifications: [],
        unreadCount: 0,
        markAsRead: () =>
        {
        },
        markAllAsRead: () =>
        {
        }
    });

export const NotificationProvider: React.FC<{ children: React.ReactNode }> = ({children}) =>
{
    const [notifications, setNotifications] = useState<NotificationDto[]>([]);
    const {appUser} = useAuth();

    const unreadCount = notifications.filter(n => !n.isRead).length;

    const markAsRead = (notificationId: string) =>
    {
        setNotifications(prev =>
            prev.map(n => n.id === notificationId ? {...n, isRead: true} : n)
        );
    };

    const markAllAsRead = () =>
    {
        setNotifications(prev =>
            prev.map(n => ({...n, isRead: true}))
        );
    };

    useEffect(() =>
    {
        if (appUser?.id)
        {
            notificationService.connect(appUser.id);

            const removeHandler = notificationService.addMessageHandler((notification) =>
            {
                setNotifications(prev => [...prev, notification]);
            });

            return () =>
            {
                removeHandler();
                notificationService.disconnect();
            };
        }
    }, [appUser?.id]);

    return (
        <NotificationContext.Provider
            value={{notifications, unreadCount, markAsRead, markAllAsRead}}>
            {children}
        </NotificationContext.Provider>
    );
};

export const useNotifications = () =>
{
    const context = useContext(NotificationContext);
    if (context === undefined)
    {
        throw new Error('useNotifications must be used within a NotificationProvider');
    }
    return context;
};