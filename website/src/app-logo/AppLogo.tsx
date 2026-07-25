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
            />
        </span>
    );
}

export default AppLogo;
