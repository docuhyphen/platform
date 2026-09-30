import {Button} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import {useInformationRequestCapabilities} from "../../../information-requests/capabilities/useInformationRequestCapabilities.ts";
import {useOperationsAccess} from "../../../information-requests/operations/useOperationsAccess.ts";
import {RequestOperationsIcon} from "../../IconBundles.tsx";

const OperationsNavigation = () =>
{
    const navigate = useNavigate();
    const capabilities = useInformationRequestCapabilities();
    const access = useOperationsAccess();

    if (!capabilities || !(capabilities.featureIncluded || capabilities.holdsRequests) || !access.canViewOperations) return null;

    return (
        <Button id={"information-request-operations-nav-btn"}
                icon={<RequestOperationsIcon/>}
                shape={"circular"}
                appearance={"subtle"}
                aria-label={"Information Request operations"}
                title={"Information Request operations"}
                onClick={() => navigate("/information-request-operations")}/>
    );
};

export default OperationsNavigation;
