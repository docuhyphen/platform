import {Button} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import {useInformationRequestCapabilities} from "../../../information-requests/capabilities/useInformationRequestCapabilities.ts";
import {ReviewQueueIcon} from "../../IconBundles.tsx";

const ReviewQueueNavigation = () =>
{
    const navigate = useNavigate();
    const capabilities = useInformationRequestCapabilities();

    if (!capabilities || !(capabilities.featureIncluded || capabilities.assignedWork)) return null;

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
