import {NotificationDto} from '../app/models/models';

class NotificationService
{
    private socket: WebSocket | null = null;
    private messageHandlers: ((notification: NotificationDto) => void)[] = [];
    private reconnectAttempts = 0;
    private maxReconnectAttempts = 5;
    private reconnectTimeout: ReturnType<typeof setTimeout> | null = null;
    private userId: string | null = null;

    connect(userId: string)
    {
        console.log("Attempting socket connection");
        this.userId = userId;

        if (this.socket)
        {
            this.socket.close();
            this.socket = null;
        }

        this.reconnectAttempts = 0;

        const token = localStorage.getItem('token');
        if (!token)
        {
            console.error("Cannot connect to WebSocket: No authentication token found");
            return;
        }

        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const host = import.meta.env.VITE_API_URL?.replace(/^https?:\/\//, '') || "localhost:8080";
        const wsUrl = `${protocol}//${host}/notifications/${userId}?token=${token}`;

        try
        {
            this.socket = new WebSocket(wsUrl);

            this.socket.onopen = () =>
            {
                console.log("WebSocket connection established");
                this.reconnectAttempts = 0;
            };

            this.socket.onmessage = (event) =>
            {
                try
                {
                    const notification = JSON.parse(event.data) as NotificationDto;
                    this.messageHandlers.forEach(handler => handler(notification));
                }
                catch (error)
                {
                    console.error("Error parsing WebSocket message:", error);
                }
            };

            this.socket.onclose = (event) =>
            {
                console.log(`WebSocket closed with code: ${event.code}, reason: ${event.reason}`);
                this.socket = null;

                if (this.userId)
                {
                    this.attemptReconnect();
                }
            };

            this.socket.onerror = (event) =>
            {
                console.log("WebSocket error:", event);
            };
        }
        catch (error)
        {
            console.error("Error creating WebSocket:", error);
            this.attemptReconnect();
        }
    }

    private attemptReconnect()
    {
        if (this.reconnectTimeout)
        {
            clearTimeout(this.reconnectTimeout);
        }

        if (this.reconnectAttempts < this.maxReconnectAttempts && this.userId)
        {
            const delay = Math.min(1000 * (2 ** this.reconnectAttempts), 10000);
            console.log(`Attempting to reconnect in ${delay}ms (attempt ${this.reconnectAttempts + 1}/${this.maxReconnectAttempts})`);

            this.reconnectTimeout = setTimeout(() =>
            {
                this.reconnectAttempts++;
                this.connect(this.userId!);
            }, delay);
        }
        else if (this.reconnectAttempts >= this.maxReconnectAttempts)
        {
            console.log("Maximum reconnection attempts reached. Please try again later.");
        }
    }

    disconnect()
    {
        if (this.reconnectTimeout)
        {
            clearTimeout(this.reconnectTimeout);
            this.reconnectTimeout = null;
        }

        this.userId = null;

        if (this.socket)
        {
            this.socket.close();
            this.socket = null;
        }
    }

    addMessageHandler(handler: (notification: NotificationDto) => void): () => void
    {
        this.messageHandlers.push(handler);

        return () =>
        {
            this.messageHandlers = this.messageHandlers.filter(h => h !== handler);
        };
    }

    sendMessage(message: unknown)
    {
        if (this.socket && this.socket.readyState === WebSocket.OPEN)
        {
            this.socket.send(JSON.stringify(message));
        }
        else
        {
            console.error("Cannot send message: WebSocket is not connected");
        }
    }
}

export const notificationService = new NotificationService();