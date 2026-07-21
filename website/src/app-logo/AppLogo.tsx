import logo from "../../assets/logo.svg";
import {useAppLogoStyles} from "./AppLogoStyles.tsx";

function AppLogo()
{
    const styles = useAppLogoStyles();

    return (
        <span id="docuhyphen-logo-container">
            <img
                id="docuhyphen-logo-image"
                src={logo}
                alt="DocuHyphen"
                className={styles.appLogo}
                width={140}
                height={80}
            />
        </span>
    );
}

export default AppLogo;
