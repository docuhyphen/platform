import {Button} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import {usePlanFeature} from "../../../../hooks/subscription/usePlanFeature.ts";
import {useOperationsAccess} from "../../../information-requests/operations/useOperationsAccess.ts";
import {PlanFeature} from "../../../models/models.tsx";
import {RequestOperationsIcon} from "../../IconBundles.tsx";

const OperationsNavigation = () =>
{
    const navigate = useNavigate();
    const feature = usePlanFeature(PlanFeature.INFORMATION_REQUESTS);
    const access = useOperationsAccess();

    if (!feature.isAvailable || !access.canViewOperations) return null;

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
