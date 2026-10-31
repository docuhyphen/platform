import {Button} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import {
    useInformationRequestCapabilities
} from "../../../information-requests/capabilities/useInformationRequestCapabilities.ts";
import {
    INFORMATION_REQUEST_ASSIGNED_WORK_PATH
} from "../../../information-requests/shared/informationRequestWorkspacePaths.ts";
import {ReviewQueueIcon} from "../../IconBundles.tsx";

const AssignedWorkNavigation = () =>
{
    const navigate = useNavigate();
    const capabilities = useInformationRequestCapabilities();

    if (!capabilities?.assignedWork) return null;

    return (
        <Button id={"information-request-assigned-work-nav-btn"}
                icon={<ReviewQueueIcon/>}
                shape={"circular"}
                appearance={"subtle"}
                aria-label={"Assigned to you"}
                title={"Assigned to you"}
                onClick={() => navigate(INFORMATION_REQUEST_ASSIGNED_WORK_PATH)}/>
    );
};

export default AssignedWorkNavigation;
