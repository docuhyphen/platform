export const deviceSessionsArticle = (
    <>
        <p>
            Device sessions shows every device currently signed in to your account and lets
            you revoke access when needed.
        </p>

        <h3>Reviewing active sessions</h3>
        <ol>
            <li>Open <b>Settings</b> and select <b>Device Sessions</b>.</li>
            <li>Review the table columns for device, IP address, last activity, created date, and expiry.</li>
            <li>Look for the <b>This device</b> badge to identify your current session.</li>
        </ol>

        <h3>Ending sessions</h3>
        <ul>
            <li>Use <b>Revoke</b> to end another device session immediately.</li>
            <li>Use <b>Sign out</b> on the current row if you want to end your current session.</li>
            <li>Use <b>Sign out of all devices</b> to revoke every active session at once.</li>
        </ul>

        <h3>What the table shows</h3>
        <ul>
            <li>The table is sorted with your current device first, then by most recent activity.</li>
            <li>Expiry dates help you see when each session will end automatically.</li>
            <li>Each row has its own action button so you can revoke a single device without affecting the others.</li>
        </ul>
    </>
);
