export const profileSettingsArticle = (
    <>
        <p id={"profile-settings-article-p-1"}>
            The Profile page gives you a quick summary of your account plus sections for
            security and sign-in alerts.
        </p>

        <h3 id={"profile-settings-article-h3-1"}>Choosing a workspace</h3>
        <p id={"profile-settings-article-p-2"}>
            Open the account menu in the top bar and choose <b id={"profile-settings-article-b-1"}>Personal workspace</b> or an
            organization you belong to. New Exchanges and Information Requests use the selected
            workspace as their owner. The selected workspace stays in effect when you return later.
        </p>

        <h3 id={"profile-settings-article-h3-2"}>Updating your basic details</h3>
        <ol id={"profile-settings-article-ol-1"}>
            <li id={"profile-settings-article-li-1"}>Open <b id={"profile-settings-article-b-2"}>Settings</b> and select <b id={"profile-settings-article-b-3"}>Profile</b>.</li>
            <li id={"profile-settings-article-li-2"}>In the profile summary, use the person edit icon beside your name.</li>
            <li id={"profile-settings-article-li-3"}>Update your first name and last name, then save.</li>
        </ol>

        <h3 id={"profile-settings-article-h3-3"}>Profile picture</h3>
        <ol id={"profile-settings-article-ol-2"}>
            <li id={"profile-settings-article-li-4"}>Open <b id={"profile-settings-article-b-4"}>Settings</b> and select <b id={"profile-settings-article-b-5"}>Profile</b>.</li>
            <li id={"profile-settings-article-li-5"}>In the profile summary, select the camera button at the bottom of your profile picture.</li>
            <li id={"profile-settings-article-li-6"}>In the dialog, select <b id={"profile-settings-article-b-6"}>Choose photo</b> and pick a PNG, JPG, WEBP or GIF image up to 5 MB.</li>
            <li id={"profile-settings-article-li-7"}>Review it in the circular preview, then select <b id={"profile-settings-article-b-7"}>Upload</b> to save.</li>
            <li id={"profile-settings-article-li-8"}>Use <b id={"profile-settings-article-b-8"}>Remove photo</b> to delete your current picture and fall back to your initials.</li>
            <li id={"profile-settings-article-li-9"}>
                Once uploaded, your picture appears beside your name across the app, including the
                account menu, the organization people and groups lists, personal group members, the
                recipient picker, and document comments. People without a picture show their initials.
            </li>
        </ol>

        <h3 id={"profile-settings-article-h3-4"}>Managing contact details</h3>
        <ul id={"profile-settings-article-ul-1"}>
            <li id={"profile-settings-article-li-10"}>Use the inline email edit icon beside your email address to update your account email.</li>
            <li id={"profile-settings-article-li-11"}>Use <b id={"profile-settings-article-b-9"}>Add phone number</b> in the profile summary to add a phone contact.</li>
            <li id={"profile-settings-article-li-12"}>When a phone number already exists, use the inline phone icon beside it to update the value.</li>
        </ul>

        <h3 id={"profile-settings-article-h3-5"}>Multi-factor authentication</h3>
        <ol id={"profile-settings-article-ol-3"}>
            <li id={"profile-settings-article-li-13"}>Open <b id={"profile-settings-article-b-10"}>Settings</b>, select <b id={"profile-settings-article-b-11"}>Profile</b>, then find <b id={"profile-settings-article-b-12"}>Security</b>.</li>
            <li id={"profile-settings-article-li-14"}>Select <b id={"profile-settings-article-b-13"}>Configure MFA</b>.</li>
            <li id={"profile-settings-article-li-15"}>Choose Google Authenticator or Microsoft Authenticator.</li>
            <li id={"profile-settings-article-li-16"}>Use the App Store or Google Play links if you need to install the selected app.</li>
            <li id={"profile-settings-article-li-17"}>Scan the QR code with the selected app and enter its current 6-digit code.</li>
            <li id={"profile-settings-article-li-18"}>Select <b id={"profile-settings-article-b-14"}>Verify and enable</b>.</li>
        </ol>
        <ul id={"profile-settings-article-ul-2"}>
            <li id={"profile-settings-article-li-19"}>Email verification is the default MFA method after sign-up.</li>
            <li id={"profile-settings-article-li-20"}>
                During first-run onboarding, you can set up Google Authenticator or Microsoft
                Authenticator after registering your profile, or skip the step and configure it later.
            </li>
            <li id={"profile-settings-article-li-21"}>
                Email fallback is disabled when you set up an authenticator app unless you explicitly
                enable <b id={"profile-settings-article-b-15"}>Allow email as a fallback</b>.
            </li>
            <li id={"profile-settings-article-li-22"}>You can return to <b id={"profile-settings-article-b-16"}>Configure MFA</b> to change fallback or switch back to email MFA.</li>
            <li id={"profile-settings-article-li-23"}>
                When email fallback is enabled, select <b id={"profile-settings-article-b-17"}>Use another method</b> while signing in and
                choose <b id={"profile-settings-article-b-18"}>Email verification code</b>.
            </li>
        </ul>

        <h3 id={"profile-settings-article-h3-6"}>Password and sign-in alerts</h3>
        <ul id={"profile-settings-article-ul-3"}>
            <li id={"profile-settings-article-li-24"}>The Security section includes password recovery guidance.</li>
            <li id={"profile-settings-article-li-25"}>Use the left navigation to open <b id={"profile-settings-article-b-19"}>Linked Accounts</b> or <b id={"profile-settings-article-b-20"}>Device Sessions</b> for deeper access management.</li>
            <li id={"profile-settings-article-li-26"}>Use the <b id={"profile-settings-article-b-21"}>Email me every time I sign in</b> switch to control sign-in alert emails.</li>
        </ul>
    </>
);
