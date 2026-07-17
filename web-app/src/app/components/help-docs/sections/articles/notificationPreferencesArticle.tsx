export const notificationPreferencesArticle = (
    <>
        <p>
            Notification preferences let you choose how DocuHyphen contacts you for each
            Exchange and document event.
        </p>

        <h3>Choosing delivery channels</h3>
        <ol>
            <li>Open <b>Settings</b> and select <b>Preferences</b>.</li>
            <li>Under Notifications, open the channel dropdown beside an event.</li>
            <li>Select <b>Email</b>, <b>Push</b>, both channels, or neither channel.</li>
        </ol>
        <p>
            Selected channels appear as tags when the dropdown is closed. Email sends the
            notification to your account email address. Push delivers it through the
            DocuHyphen in-app notification channel. To also receive native browser alerts
            on the current device, select <b>Enable</b> beside <b>Browser notifications</b>
            and approve the browser permission prompt. Selecting no channels disables that
            event notification for your account.
        </p>
        <p>
            Browser permission applies only to the browser and device where you grant it.
            If permission is blocked, enable notifications for the DocuHyphen site in your
            browser settings, return to Preferences, and select <b>Request again</b>. In-app
            Push notifications continue to appear in DocuHyphen even when browser alerts
            are blocked or unsupported.
        </p>

        <h3>Notification bell</h3>
        <p>
            The notification bell in the header shows unread in-app notifications and
            pending approvals. Recent notifications load when you sign in, and new
            notifications or approval assignments appear automatically while the app is
            open. The Requests count and Exchange list also refresh automatically when a
            new Exchange becomes available to you. Approval notifications identify the
            Exchange and requester by name when that context is available, while internal
            resource codes and identifiers remain hidden. Older notifications load in
            batches as you scroll to the bottom of the list. You can also select
            <b>Load more</b> when it is shown.
        </p>
        <p>
            Select an Exchange notification to open that Exchange. Notifications about a
            specific document open the Exchange with that document selected. The same
            navigation applies when you select a native browser alert.
        </p>

        <h3>Exchange notifications</h3>
        <ul>
            <li><b>Exchange initiated</b> covers Exchanges sent to you and Exchanges you send.</li>
            <li><b>Exchange accepted</b> covers recipient acceptance of an Exchange you initiated.</li>
            <li><b>Exchange declined</b> covers recipient rejection of an Exchange you initiated.</li>
            <li><b>Exchange ended</b> covers Exchanges that move to their completed state.</li>
        </ul>

        <h3>Document notifications</h3>
        <ul>
            <li>
                <b>Document notes and comments</b> covers new shared collaboration notes or comments.
                Notifications identify the commenter, document, and Exchange when that context is
                available. Internal notes do not send collaboration notifications.
            </li>
            <li><b>Document deleted</b> covers documents removed from an Exchange.</li>
            <li><b>Document added</b> covers new documents added to an Exchange.</li>
            <li><b>Document uploaded</b> covers initial files and newly uploaded file versions.</li>
        </ul>

        <h3>Trusted Organization administration</h3>
        <p>
            Active organization administrators receive email and in-app notifications for
            Trusted Organization requests, decisions, policy changes, suspensions, resumptions,
            and endings. These administrative notifications are not controlled by the Exchange
            and document event preferences above.
        </p>

        <p>
            Changes save immediately. Available events still respect your Exchange access
            and organization permissions.
        </p>
    </>
);
