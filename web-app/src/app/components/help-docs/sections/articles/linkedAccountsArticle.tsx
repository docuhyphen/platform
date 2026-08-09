export const linkedAccountsArticle = (
    <>
        <p>
            Linked accounts lets you connect and manage the sign-in methods attached to
            your DocuHyphen account.
        </p>

        <h3>Connecting a provider</h3>
        <ol>
            <li>Open <b>Settings</b> and select <b>Linked Accounts</b>.</li>
            <li>Review the provider cards for Email & Password, Microsoft, and Google.</li>
            <li>Select <b>Link</b> on any available provider you want to connect.</li>
        </ol>

        <h3>Unlinking a provider</h3>
        <ul>
            <li>Connected providers show an <b>Unlink</b> action on the right side of the card.</li>
            <li>You must keep at least one sign-in method connected to your account.</li>
            <li>When only one provider remains, unlink is disabled and the card explains why.</li>
            <li>
                Changing your sign-in methods is a sensitive action, so you may be asked to sign
                in again first. If your last sign-in was more than five minutes ago, unlink is
                refused until you re-authenticate.
            </li>
        </ul>

        <h3>Linking Microsoft or Google to an existing account</h3>
        <ul>
            <li>
                When the provider account uses the same email address as an existing DocuHyphen
                account, you are asked to confirm your DocuHyphen password before the two are
                linked.
            </li>
            <li>
                A new account can only be created from a provider sign-in when the provider
                confirms that you own the email address. Microsoft only confirms this for
                verified domains in the directory the sign-in came from.
            </li>
            <li>
                If the provider does not confirm ownership, sign up with that email address
                first, then link the provider from this page.
            </li>
        </ul>

        <h3>Setting a password</h3>
        <ul>
            <li>
                Adding a password to a provider-only account requires a recent sign-in, and
                changing an existing password requires your current one.
            </li>
            <li>Setting or changing a password signs you out on every other device.</li>
        </ul>

        <h3>What the page shows</h3>
        <ul>
            <li>The summary strip shows how many sign-in methods are currently connected.</li>
            <li>Each provider card shows its connection status and a short description.</li>
            <li>
                You can sign in using a connected provider when it is also enabled by your
                organization&apos;s sign-in policy.
            </li>
        </ul>
    </>
);
