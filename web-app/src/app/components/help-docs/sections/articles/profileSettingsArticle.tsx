export const profileSettingsArticle = (
    <>
        <p>
            The Profile page gives you a quick summary of your account plus sections for
            security and sign-in alerts.
        </p>

        <h3>Updating your basic details</h3>
        <ol>
            <li>Open <b>Settings</b> and select <b>Profile</b>.</li>
            <li>In the profile summary, use the person edit icon beside your name.</li>
            <li>Update your first name and last name, then save.</li>
        </ol>

        <h3>Profile picture</h3>
        <ol>
            <li>Open <b>Settings</b> and select <b>Profile</b>.</li>
            <li>In the profile summary, select the camera button at the bottom of your profile picture.</li>
            <li>In the dialog, select <b>Choose photo</b> and pick a PNG, JPG, WEBP or GIF image up to 5 MB.</li>
            <li>Review it in the circular preview, then select <b>Upload</b> to save.</li>
            <li>Use <b>Remove photo</b> to delete your current picture and fall back to your initials.</li>
        </ol>

        <h3>Managing contact details</h3>
        <ul>
            <li>Use the inline email edit icon beside your email address to update your account email.</li>
            <li>Use <b>Add phone number</b> in the profile summary to add a phone contact.</li>
            <li>When a phone number already exists, use the inline phone icon beside it to update the value.</li>
        </ul>

        <h3>Multi-factor authentication</h3>
        <ol>
            <li>Open <b>Settings</b>, select <b>Profile</b>, then find <b>Security</b>.</li>
            <li>Select <b>Configure MFA</b>.</li>
            <li>Choose Google Authenticator or Microsoft Authenticator.</li>
            <li>Scan the QR code with the selected app and enter its current 6-digit code.</li>
            <li>Select <b>Verify and enable</b>.</li>
        </ol>
        <ul>
            <li>Email verification is the default MFA method after sign-up.</li>
            <li>
                Email fallback is disabled when you set up an authenticator app unless you explicitly
                enable <b>Allow email as a fallback</b>.
            </li>
            <li>You can return to <b>Configure MFA</b> to change fallback or switch back to email MFA.</li>
            <li>
                When email fallback is enabled, select <b>Use another method</b> while signing in and
                choose <b>Email verification code</b>.
            </li>
        </ul>

        <h3>Password and sign-in alerts</h3>
        <ul>
            <li>The Security section includes password recovery guidance.</li>
            <li>Use the left navigation to open <b>Linked Accounts</b> or <b>Device Sessions</b> for deeper access management.</li>
            <li>Use the <b>Email me every time I sign in</b> switch to control sign-in alert emails.</li>
        </ul>
    </>
);
