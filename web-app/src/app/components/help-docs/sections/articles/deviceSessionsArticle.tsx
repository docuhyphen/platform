export const deviceSessionsArticle = (
    <>
        <p id={"device-sessions-article-p-1"}>
            Device sessions shows your active and ended sign-ins so you can review which
            devices still have access and clean up old records.
        </p>

        <h3 id={"device-sessions-article-h3-1"}>Reviewing sessions</h3>
        <ol id={"device-sessions-article-ol-1"}>
            <li id={"device-sessions-article-li-1"}>Open <b id={"device-sessions-article-b-1"}>Settings</b> and select <b id={"device-sessions-article-b-2"}>Device Sessions</b>.</li>
            <li id={"device-sessions-article-li-2"}>Review the table columns for device, IP address, last activity, created date, and expiry.</li>
            <li id={"device-sessions-article-li-3"}>Look for the <b id={"device-sessions-article-b-3"}>This device</b> badge to identify your current session.</li>
            <li id={"device-sessions-article-li-4"}>Ended sessions stay in the table with a <b id={"device-sessions-article-b-4"}>Revoked</b> or <b id={"device-sessions-article-b-5"}>Expired</b> status.</li>
        </ol>

        <h3 id={"device-sessions-article-h3-2"}>Ending sessions</h3>
        <ul id={"device-sessions-article-ul-1"}>
            <li id={"device-sessions-article-li-5"}>Use <b id={"device-sessions-article-b-6"}>Revoke</b> to end another device session immediately.</li>
            <li id={"device-sessions-article-li-6"}>Use <b id={"device-sessions-article-b-7"}>Sign out</b> on the current row if you want to end your current session.</li>
            <li id={"device-sessions-article-li-7"}>Use <b id={"device-sessions-article-b-8"}>Sign out of all devices</b> to revoke every active session at once.</li>
        </ul>

        <h3 id={"device-sessions-article-h3-3"}>Inactivity warning</h3>
        <ul id={"device-sessions-article-ul-2"}>
            <li id={"device-sessions-article-li-8"}>A warning dialog appears one minute before your session reaches its inactivity limit or maximum duration.</li>
            <li id={"device-sessions-article-li-9"}>For inactivity warnings, select <b id={"device-sessions-article-b-9"}>Continue session</b> to remain signed in and restart the inactivity timer.</li>
            <li id={"device-sessions-article-li-10"}>Maximum-duration warnings cannot be extended. Save your work before signing in again.</li>
            <li id={"device-sessions-article-li-11"}>Select <b id={"device-sessions-article-b-10"}>Sign out</b> to end the session immediately.</li>
            <li id={"device-sessions-article-li-12"}>If the countdown reaches zero, you are signed out automatically and must sign in again.</li>
        </ul>

        <h3 id={"device-sessions-article-h3-4"}>Deleting old records</h3>
        <ul id={"device-sessions-article-ul-3"}>
            <li id={"device-sessions-article-li-13"}>Use <b id={"device-sessions-article-b-11"}>Delete</b> on an ended session if you no longer want to keep that row in your history.</li>
            <li id={"device-sessions-article-li-14"}>Active sessions must be ended first before they can be deleted.</li>
        </ul>

        <h3 id={"device-sessions-article-h3-5"}>What the table shows</h3>
        <ul id={"device-sessions-article-ul-4"}>
            <li id={"device-sessions-article-li-15"}>The table is sorted with your current device first, then active sessions, then ended sessions.</li>
            <li id={"device-sessions-article-li-16"}>Expiry dates help you see when each active session will end automatically.</li>
            <li id={"device-sessions-article-li-17"}>Each row has its own action button so you can revoke or delete one session without affecting the others.</li>
        </ul>
    </>
);
