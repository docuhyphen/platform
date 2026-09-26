import {Button} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import {usePlanFeature} from "../../../../hooks/subscription/usePlanFeature.ts";
import {PlanFeature} from "../../../models/models.tsx";
import {ReviewQueueIcon} from "../../IconBundles.tsx";

const ReviewQueueNavigation = () =>
{
    const navigate = useNavigate();
    const feature = usePlanFeature(PlanFeature.INFORMATION_REQUESTS);

    if (!feature.isAvailable) return null;

    return (
        <Button id={"information-request-reviews-nav-btn"}
                icon={<ReviewQueueIcon/>}
                shape={"circular"}
                appearance={"subtle"}
                aria-label={"Reviews assigned to you"}
                title={"Reviews assigned to you"}
                onClick={() => navigate("/information-request-reviews")}/>
    );
};

export default ReviewQueueNavigation;
