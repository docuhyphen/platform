import {Button} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {Capability} from "../../../models/models.tsx";
import {AuditIcon, SettingsAppAdminsIcon} from "../../IconBundles.tsx";

const PlatformNavigation = () =>
{
    const {hasCapability} = useAuth();
    const navigate = useNavigate();

    return (
        <>
            {hasCapability(Capability.APP_ADMIN) && (
                <Button
                    id={"platform-administration-nav-btn"}
                    icon={<SettingsAppAdminsIcon/>}
                    shape={"circular"}
                    appearance={"subtle"}
                    aria-label={"Platform Administration"}
                    title={"Platform Administration"}
                    onClick={() => navigate("/platform/administration")}/>
            )}
            {hasCapability(Capability.APP_AUDIT_READ) && (
                <Button
                    id={"platform-audit-nav-btn"}
                    icon={<AuditIcon/>}
                    shape={"circular"}
                    appearance={"subtle"}
                    aria-label={"Platform Audit"}
                    title={"Platform Audit"}
                    onClick={() => navigate("/platform/audit")}/>
            )}
        </>
    );
};

export default PlatformNavigation;
