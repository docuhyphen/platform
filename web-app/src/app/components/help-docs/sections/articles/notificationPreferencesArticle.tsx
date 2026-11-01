export const notificationPreferencesArticle = (
    <>
        <p id={"notification-preferences-intro-paragraph"}>
            Notification preferences let you choose how DocuHyphen contacts you for each
            Exchange and document event.
        </p>

        <h3 id={"notification-preferences-choosing-delivery-channels-heading"}>Choosing delivery channels</h3>
        <ol id={"notification-preferences-choosing-delivery-channels-list"}>
            <li id={"notification-preferences-article-li-1"}>Open <b id={"notification-preferences-article-b-1"}>Settings</b> and select <b id={"notification-preferences-article-b-2"}>Preferences</b>.</li>
            <li id={"notification-preferences-article-li-2"}>Under Notifications, open the channel dropdown beside an event.</li>
            <li id={"notification-preferences-article-li-3"}>Select <b id={"notification-preferences-article-b-3"}>Email</b>, <b id={"notification-preferences-article-b-4"}>Push</b>, both channels, or neither channel.</li>
        </ol>
        <p id={"notification-preferences-choosing-delivery-channels-paragraph"}>
            Selected channels appear as tags when the dropdown is closed. Email sends the
            notification to your account email address. Push delivers it through the
            DocuHyphen in-app notification channel. To also receive native browser alerts
            on the current device, select <b id={"notification-preferences-article-b-5"}>Enable</b> beside <b id={"notification-preferences-article-b-6"}>Browser notifications</b>{" "}
            and approve the browser permission prompt. Selecting no channels disables that
            event notification for your account.
        </p>
        <p id={"notification-preferences-choosing-delivery-channels-paragraph-2"}>
            Browser permission applies only to the browser and device where you grant it.
            If permission is blocked, enable notifications for the DocuHyphen site in your
            browser settings, return to Preferences, and select <b id={"notification-preferences-article-b-7"}>Request again</b>. In-app
            Push notifications continue to appear in DocuHyphen even when browser alerts
            are blocked or unsupported.
        </p>

        <h3 id={"notification-preferences-notification-bell-heading"}>Notification bell</h3>
        <p id={"notification-preferences-notification-bell-paragraph"}>
            The notification bell in the header shows unread in-app notifications and
            pending approvals. Recent notifications load when you sign in, and new
            notifications or approval assignments appear automatically while the app is
            open. The Requests count and Exchange list also refresh automatically when a
            new Exchange becomes available to you. Approval notifications identify the
            Exchange and requester by name when that context is available, while internal
            resource codes and identifiers remain hidden. Older notifications load in
            batches as you scroll to the bottom of the list. You can also select{" "}
            <b id={"notification-preferences-article-b-8"}>Load more</b> when it is shown.
        </p>
        <p id={"notification-preferences-notification-bell-paragraph-2"}>
            Select an Exchange notification to open that Exchange. Notifications about a
            specific document open the Exchange with that document selected. The same
            navigation applies when you select a native browser alert.
        </p>
        <p id={"notification-preferences-notification-bell-paragraph-3"}>
            Opening a notified item directly also clears its unread notification when
            you view its workflow or document comments.
        </p>

        <h3 id={"notification-preferences-exchange-notifications-heading"}>Exchange notifications</h3>
        <ul id={"notification-preferences-exchange-notifications-list"}>
            <li id={"notification-preferences-article-li-4"}><b id={"notification-preferences-article-b-9"}>Exchange initiated</b> covers Exchanges sent to you.</li>
            <li id={"notification-preferences-article-li-5"}><b id={"notification-preferences-article-b-10"}>Exchange accepted</b> covers recipient acceptance of an Exchange you initiated.</li>
            <li id={"notification-preferences-article-li-6"}><b id={"notification-preferences-article-b-11"}>Exchange declined</b> covers recipient rejection of an Exchange you initiated.</li>
            <li id={"notification-preferences-article-li-7"}><b id={"notification-preferences-article-b-12"}>Exchange ended</b> covers Exchanges that move to their completed state.</li>
        </ul>

        <h3 id={"notification-preferences-document-notifications-heading"}>Document notifications</h3>
        <ul id={"notification-preferences-document-notifications-list"}>
            <li id={"notification-preferences-article-li-8"}>
                <b id={"notification-preferences-article-b-13"}>Document notes and comments</b> covers new shared collaboration notes or comments.
                Notifications identify the commenter, document, and Exchange when that context is
                available. Internal notes do not send collaboration notifications.
            </li>
            <li id={"notification-preferences-article-li-9"}><b id={"notification-preferences-article-b-14"}>Document deleted</b> covers documents removed from an Exchange.</li>
            <li id={"notification-preferences-article-li-10"}><b id={"notification-preferences-article-b-15"}>Document added</b> covers new documents added to an Exchange.</li>
            <li id={"notification-preferences-article-li-11"}><b id={"notification-preferences-article-b-16"}>Document uploaded</b> covers initial files and newly uploaded file versions.</li>
        </ul>

        <h3 id={"notification-preferences-trusted-organization-administration-heading"}>Trusted Organization
            administration</h3>
        <p id={"notification-preferences-trusted-organization-administration-paragraph"}>
            Active organization administrators receive email and in-app notifications for
            Trusted Organization requests, decisions, policy changes, suspensions, resumptions,
            and endings. These administrative notifications are not controlled by the Exchange
            and document event preferences above.
        </p>

        <h3 id={"notification-preferences-trial-request-administration-heading"}>Trial request administration</h3>
        <p id={"notification-preferences-trial-request-administration-paragraph"}>
            Active App Administrators receive an in-app notification when an eligible user or
            organization billing user requests a trial. The earliest effective App Administrator also
            receives an email. The requester receives an in-app notification after approval or rejection.
            These administrative notifications are not controlled by the
            Exchange and document event preferences above. Selecting a new-request notification opens
            Trial Requests; selecting a decision notification opens Billing.
        </p>

        <p id={"notification-preferences-trial-request-administration-paragraph-2"}>
            Changes save immediately. Available events still respect your Exchange access
            and organization permissions. You can manage your personal notification preferences
            whether or not your account belongs to an organization.
        </p>
    </>
);
