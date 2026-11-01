export const linkedAccountsArticle = (
    <>
        <p id={"linked-accounts-article-p-1"}>
            Linked accounts lets you connect and manage the sign-in methods attached to
            your DocuHyphen account.
        </p>

        <h3 id={"linked-accounts-article-h3-1"}>Connecting a provider</h3>
        <ol id={"linked-accounts-article-ol-1"}>
            <li id={"linked-accounts-article-li-1"}>Open <b id={"linked-accounts-article-b-1"}>Settings</b> and select <b id={"linked-accounts-article-b-2"}>Linked Accounts</b>.</li>
            <li id={"linked-accounts-article-li-2"}>Review the provider cards for Email & Password, Microsoft, and Google.</li>
            <li id={"linked-accounts-article-li-3"}>Select <b id={"linked-accounts-article-b-3"}>Link</b> on any available provider you want to connect.</li>
            <li id={"linked-accounts-article-li-4"}>
                Linking Email & Password first asks you to confirm your existing sign-in method.
                After verification, DocuHyphen returns to Linked Accounts and opens the password
                setup dialog.
            </li>
        </ol>

        <h3 id={"linked-accounts-article-h3-2"}>Unlinking a provider</h3>
        <ul id={"linked-accounts-article-ul-1"}>
            <li id={"linked-accounts-article-li-5"}>Connected providers show an <b id={"linked-accounts-article-b-4"}>Unlink</b> action on the right side of the card.</li>
            <li id={"linked-accounts-article-li-6"}>You must confirm the selected provider before DocuHyphen unlinks it.</li>
            <li id={"linked-accounts-article-li-7"}>You must keep at least one sign-in method connected to your account.</li>
            <li id={"linked-accounts-article-li-8"}>When only one provider remains, unlink is disabled and the card explains why.</li>
            <li id={"linked-accounts-article-li-9"}>
                Changing your sign-in methods is a sensitive action, so you may be asked to sign
                in again first. If your last sign-in was more than five minutes ago, unlink is
                refused until you re-authenticate.
            </li>
        </ul>

        <h3 id={"linked-accounts-article-h3-3"}>Linking Microsoft or Google to an existing account</h3>
        <ul id={"linked-accounts-article-ul-2"}>
            <li id={"linked-accounts-article-li-10"}>
                When the provider account uses the same email address as an existing DocuHyphen
                account, you are asked to confirm your DocuHyphen password before the two are
                linked.
            </li>
            <li id={"linked-accounts-article-li-11"}>
                A new account can only be created from a provider sign-in when the provider
                confirms that you own the email address. Microsoft only confirms this for
                verified domains in the directory the sign-in came from.
            </li>
            <li id={"linked-accounts-article-li-12"}>
                If the provider does not confirm ownership, sign up with that email address
                first, then link the provider from this page.
            </li>
        </ul>

        <h3 id={"linked-accounts-article-h3-4"}>Setting a password</h3>
        <ul id={"linked-accounts-article-ul-3"}>
            <li id={"linked-accounts-article-li-13"}>
                Adding a password to a provider-only account requires a recent sign-in, and
                changing an existing password requires your current one.
            </li>
            <li id={"linked-accounts-article-li-14"}>
                After the password is added, the confirmation dialog explains that all sessions
                were signed out and lets you continue to the sign-in page.
            </li>
        </ul>

        <h3 id={"linked-accounts-article-h3-5"}>What the page shows</h3>
        <ul id={"linked-accounts-article-ul-4"}>
            <li id={"linked-accounts-article-li-15"}>The summary strip shows how many sign-in methods are currently connected.</li>
            <li id={"linked-accounts-article-li-16"}>Each provider card shows its connection status and a short description.</li>
            <li id={"linked-accounts-article-li-17"}>
                You can sign in using a connected provider when it is also enabled by your
                organization&apos;s sign-in policy.
            </li>
        </ul>
    </>
);
