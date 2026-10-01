import {Caption1, Divider, Link, Text} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import {useSignInAccountLinksStyles} from "./SignInAccountLinksStyles.tsx";

interface SignInAccountLinksProps
{
    disabled: boolean;
}

const SignInAccountLinks = ({disabled}: SignInAccountLinksProps) =>
{
    const navigate = useNavigate();
    const styles = useSignInAccountLinksStyles();

    return <div
        id={"sign-in-account-links"}
        className={styles.accountLinks}>
        <Caption1 id={"sign-in-recover-account-caption"}>
            Forgot your sign in credentials? &nbsp;
            <Link
                id={"sign-in-recover-account-link"}
                onClick={() => navigate("/account-recovery")}
                disabled={disabled}>
                <Text weight="semibold">Recover account</Text>
            </Link>
        </Caption1>
        <Divider id={"sign-in-account-links-divider"}> OR </Divider>
        <Caption1 id={"sign-in-sign-up-caption"}>
            Don't have an account? &nbsp;
            <Link
                id={"sign-in-sign-up-link"}
                onClick={() => navigate("/sign-up")}
                disabled={disabled}>
                <Text weight="semibold">Sign up</Text>
            </Link>
        </Caption1>
    </div>;
};

export default SignInAccountLinks;
