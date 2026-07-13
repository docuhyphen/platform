export const deviceSessionsArticle = (
    <>
        <p>
            Device sessions shows your active and ended sign-ins so you can review which
            devices still have access and clean up old records.
        </p>

        <h3>Reviewing sessions</h3>
        <ol>
            <li>Open <b>Settings</b> and select <b>Device Sessions</b>.</li>
            <li>Review the table columns for device, IP address, last activity, created date, and expiry.</li>
            <li>Look for the <b>This device</b> badge to identify your current session.</li>
            <li>Ended sessions stay in the table with a <b>Revoked</b> or <b>Expired</b> status.</li>
        </ol>

        <h3>Ending sessions</h3>
        <ul>
            <li>Use <b>Revoke</b> to end another device session immediately.</li>
            <li>Use <b>Sign out</b> on the current row if you want to end your current session.</li>
            <li>Use <b>Sign out of all devices</b> to revoke every active session at once.</li>
        </ul>

        <h3>Inactivity warning</h3>
        <ul>
            <li>A warning dialog appears one minute before your session reaches its inactivity limit.</li>
            <li>Select <b>Continue session</b> to remain signed in and restart the inactivity timer.</li>
            <li>Select <b>Sign out</b> to end the session immediately.</li>
            <li>If the countdown reaches zero, you are signed out automatically and must sign in again.</li>
        </ul>

        <h3>Deleting old records</h3>
        <ul>
            <li>Use <b>Delete</b> on an ended session if you no longer want to keep that row in your history.</li>
            <li>Active sessions must be ended first before they can be deleted.</li>
        </ul>

        <h3>What the table shows</h3>
        <ul>
            <li>The table is sorted with your current device first, then active sessions, then ended sessions.</li>
            <li>Expiry dates help you see when each active session will end automatically.</li>
            <li>Each row has its own action button so you can revoke or delete one session without affecting the others.</li>
        </ul>
    </>
);
