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
            DocuHyphen in-app notification channel. Selecting no channels disables that
            notification for your account.
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
                Internal notes do not send collaboration notifications.
            </li>
            <li><b>Document deleted</b> covers documents removed from an Exchange.</li>
            <li><b>Document added</b> covers new documents added to an Exchange.</li>
            <li><b>Document uploaded</b> covers initial files and newly uploaded file versions.</li>
        </ul>

        <p>
            Changes save immediately. Available events still respect your Exchange access
            and organization permissions.
        </p>
    </>
);
